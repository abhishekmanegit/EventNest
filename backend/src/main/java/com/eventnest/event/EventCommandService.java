package com.eventnest.event;

import com.eventnest.category.Category;
import com.eventnest.category.CategoryRepository;
import com.eventnest.college.College;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ForbiddenException;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.common.util.SlugUtil;
import com.eventnest.event.dto.EventDtos.EventDetail;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.event.dto.EventDtos.EventWriteRequest;
import com.eventnest.event.dto.EventDtos.StatusActionRequest;
import com.eventnest.organizer.Organizer;
import com.eventnest.organizer.OrganizerRepository;
import com.eventnest.organizer.OrganizerVerificationStatus;
import com.eventnest.notification.EventNotificationDispatcher;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the events feature.
 *
 * <p>Every public method here starts the same two-step dance, and the order is
 * deliberate:
 * <ol>
 *   <li><b>Who are you?</b> - resolve the organizer and require them to be
 *       approved (decision D19: verified once, then free to publish).</li>
 *   <li><b>Is it yours?</b> - {@code findByIdAndOrganizerUserId}. The database
 *       returns nothing for an event someone else owns, so ownership cannot be
 *       forgotten the way an {@code if (event.getOrganizerId() == me)} check
 *       spread across six methods can. This is authorisation layer 3, and it
 *       exists because layers 1 and 2 (URL matchers, {@code @PreAuthorize}) only
 *       prove a caller <em>is an organizer</em>, never <em>which</em> organizer.</li>
 * </ol>
 *
 * <p>One transaction per public method: a create is a slug allocation plus an
 * insert, a status change is a state check plus a write, and neither should be
 * half-applied.
 */
@Service
@RequiredArgsConstructor
public class EventCommandService {

    private final EventRepository eventRepository;
    private final OrganizerRepository organizerRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    /**
     * Optional-by-behaviour, not by wiring: the bean always exists, but does
     * nothing while {@code app.notifications.enabled} is false. Splitting the
     * fan-out into its own class keeps the write side of events free of
     * "who should we tell about this?" knowledge.
     */
    private final EventNotificationDispatcher eventNotificationDispatcher;
    private final Clock clock;

    // ----------------------------------------------------------------- read --

    /**
     * The organizer's own list, including drafts. Public discovery only ever shows
     * {@code PUBLISHED}, so returning everything here cannot leak - and hiding
     * drafts from their own author would be actively unhelpful.
     */
    @Transactional(readOnly = true)
    public List<EventSummary> mine(String status, Long organizerUserId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60));
        var result = isBlank(status)
                ? eventRepository.findByOrganizerUserIdOrderByStartsAtDesc(organizerUserId, pageable)
                : parseStatus(status)
                        .map(s -> eventRepository.findByOrganizerUserIdAndStatusOrderByStartsAtDesc(
                                organizerUserId, s, pageable))
                        .orElseThrow(() -> new BadRequestException(
                                "Unknown status '" + status + "'. Use one of: DRAFT, PENDING_REVIEW, "
                                        + "PUBLISHED, REJECTED, CANCELLED, COMPLETED."));
        return eventMapper.toSummaryList(result.getContent());
    }

    @Transactional(readOnly = true)
    public EventDetail detail(Long id, Long organizerUserId) {
        Event event = owned(id, organizerUserId);
        return eventMapper.toDetail(event);
    }

    /**
     * Dashboard numbers. Counting with repository methods rather than loading rows
     * means the dashboard is four cheap {@code COUNT} queries against
     * {@code ix_events_organizer} instead of a table scan to count in Java.
     */
    @Transactional(readOnly = true)
    public OrganizerDashboard dashboard(Long organizerUserId) {
        List<Event> upcoming = eventRepository
                .findByOrganizerUserIdOrderByStartsAtDesc(organizerUserId, PageRequest.of(0, 5))
                .getContent().stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .toList();
        return new OrganizerDashboard(
                eventRepository.countByOrganizerUserId(organizerUserId),
                eventRepository.countByOrganizerUserIdAndStatus(organizerUserId, EventStatus.PUBLISHED),
                eventRepository.countByOrganizerUserIdAndStatus(organizerUserId, EventStatus.DRAFT),
                eventRepository.countByOrganizerUserIdAndStatus(organizerUserId, EventStatus.PENDING_REVIEW),
                eventMapper.toSummaryList(upcoming));
    }

    public record OrganizerDashboard(
            long totalEvents,
            long publishedEvents,
            long draftEvents,
            long pendingReviewEvents,
            List<EventSummary> upcoming) {
    }

    // --------------------------------------------------------------- create --

    /**
     * Creates an event.
     *
     * <p><b>{@code publish} is an input, not a status field.</b> Letting the client
     * send {@code status = "PUBLISHED"} directly would let it skip the check that
     * decides between publishing and entering the review queue. Passing a boolean
     * expresses what the user actually asked for ("save draft" vs "publish now"),
     * and the service decides what that means for this college.
     *
     * <p>The two outcomes are: straight to {@code PUBLISHED} for a normal college,
     * or {@code PENDING_REVIEW} when the college has opted into moderation. Both
     * are handled by one code path because the caller's intent is identical.
     */
    @Transactional
    public EventDetail create(EventWriteRequest request, boolean publish, Long organizerUserId) {
        Organizer organizer = requireApprovedOrganizer(organizerUserId);
        validate(request);

        College college = organizer.getCollege();
        Event event = new Event();
        event.setOrganizer(organizer);
        event.setCollege(college);
        event.setCategory(requireCategory(request.categoryId()));
        apply(event, request);

        event.setSlug(SlugUtil.uniqueSlug(request.title(), eventRepository::existsBySlug));
        event.setModerationRequired(college.isModerationRequired());

        Instant now = Instant.now(clock);
        if (publish) {
            boolean needsReview = college.isModerationRequired();
            event.setStatus(needsReview ? EventStatus.PENDING_REVIEW : EventStatus.PUBLISHED);
            if (!needsReview) {
                event.setPublishedAt(now);
            }
        } else {
            event.setStatus(EventStatus.DRAFT);
        }

        Event saved = eventRepository.save(event);
        if (saved.getStatus() == EventStatus.PUBLISHED) {
            eventNotificationDispatcher.onPublished(saved);
        }
        return eventMapper.toDetail(saved);
    }

    // --------------------------------------------------------------- update --

    @Transactional
    public EventDetail update(Long id, EventWriteRequest request, Long organizerUserId) {
        requireApprovedOrganizer(organizerUserId);
        Event event = owned(id, organizerUserId);
        if (!event.getStatus().isOpenForEditing() && event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException(
                    "This event is " + event.getStatus().getLabel().toLowerCase()
                            + " and can no longer be edited.");
        }
        validate(request);

        apply(event, request);
        event.setCategory(requireCategory(request.categoryId()));

        // Re-derive moderationRequired from the college rather than trusting whatever
        // the client sent: it is a platform policy flag, not event content.
        event.setModerationRequired(event.getCollege().isModerationRequired());
        return eventMapper.toDetail(eventRepository.save(event));
    }

    /**
     * The status state machine ({@link EventStatus#canTransitionTo}).
     *
     * <p>Modelled as an action rather than a raw status patch on purpose: letting
     * a client write any status it likes would make the state machine decorative,
     * and "publish" would be reachable from CANCELLED. An action also carries the
     * reason a cancellation needs, which a status field cannot.
     */
    @Transactional
    public EventDetail changeStatus(Long id, StatusActionRequest request, Long organizerUserId) {
        Organizer organizer = requireApprovedOrganizer(organizerUserId);
        Event event = owned(id, organizerUserId);

        EventStatus target = switch (request.action()) {
            case SUBMIT -> {
                if (!event.isModerationRequired()) {
                    // Not a failure: this college opted out of moderation, so there is
                    // nothing to submit to. Returning the event unchanged is more
                    // useful than an error the UI would have to special-case.
                    yield event.getStatus();
                }
                yield EventStatus.PENDING_REVIEW;
            }
            case PUBLISH -> {
                if (event.isModerationRequired()) {
                    throw new ForbiddenException(
                            "Events for this college must be reviewed before they can be published.");
                }
                yield EventStatus.PUBLISHED;
            }
            case CANCEL -> EventStatus.CANCELLED;
            case COMPLETE -> EventStatus.COMPLETED;
        };

        if (target == event.getStatus()) {
            return eventMapper.toDetail(event);
        }
        if (!event.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot move an event from " + event.getStatus().getLabel()
                            + " to " + target.getLabel() + ".");
        }

        applyTransition(event, target, request.reason(), organizer);
        Event saved = eventRepository.save(event);
        if (target == EventStatus.PUBLISHED) {
            eventNotificationDispatcher.onPublished(saved);
        }
        return eventMapper.toDetail(saved);
    }

    /**
     * Deletes a draft. Published events are cancelled, never deleted: students may
     * already hold links to them, and removing the row would silently destroy
     * registrations and receipts.
     */
    @Transactional
    public void delete(Long id, Long organizerUserId) {
        requireApprovedOrganizer(organizerUserId);
        Event event = owned(id, organizerUserId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ConflictException(
                    "Only draft events can be deleted. Cancel this event instead - "
                            + "its registrations and history are kept.");
        }
        eventRepository.delete(event);
    }

    // ------------------------------------------------------------- internals --

    private void applyTransition(Event event, EventStatus target, String reason, Organizer organizer) {
        Instant now = Instant.now(clock);
        switch (target) {
            case PUBLISHED -> {
                if (event.getPublishedAt() == null) {
                    event.setPublishedAt(now);
                }
                event.setCancellationReason(null);
                event.setCancelledAt(null);
            }
            case CANCELLED -> {
                // The organizer can cancel their own event without an admin; the
                // reason is recorded because students are notified with it.
                event.setCancelledAt(now);
                event.setCancellationReason(reason);
            }
            case COMPLETED, PENDING_REVIEW -> { /* no side effects */ }
            default -> { /* DRAFT / REJECTED are reached from the actions above */ }
        }
        event.setStatus(target);
    }

    private void apply(Event event, EventWriteRequest request) {
        event.setTitle(request.title().trim());
        event.setSummary(blankToNull(request.summary()));
        event.setDescription(request.description().trim());
        event.setPosterUrl(blankToNull(request.posterUrl()));
        event.setBannerUrl(blankToNull(request.bannerUrl()));
        event.setMode(request.mode());
        event.setVenue(blankToNull(request.venue()));
        event.setAddressLine(blankToNull(request.addressLine()));
        event.setCity(blankToNull(request.city()));
        event.setDistrict(blankToNull(request.district()));
        event.setState(blankToNull(request.state()));
        event.setCountry(blankToNull(request.country()));
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setRegistrationDeadline(request.registrationDeadline());
        event.setRegistrationMode(request.registrationMode());
        event.setMaxParticipants(request.maxParticipants());
        event.setTeamSizeMin(request.teamSizeMin());
        event.setTeamSizeMax(request.teamSizeMax());
        event.setPrizeTitle(blankToNull(request.prizeTitle()));
        event.setPrizeAmount(request.prizeAmount());
        event.setPrizeCurrency(request.prizeCurrency());
        event.setPrizeDescription(blankToNull(request.prizeDescription()));
        event.setEligibility(blankToNull(request.eligibility()));
        event.setRules(blankToNull(request.rules()));
        event.setCoordinatorName(blankToNull(request.coordinatorName()));
        event.setCoordinatorEmail(blankToNull(request.coordinatorEmail()));
        event.setCoordinatorPhone(blankToNull(request.coordinatorPhone()));
        event.setTags(request.tags() == null ? new String[0]
                : request.tags().stream().filter(t -> !isBlank(t)).map(String::trim).toArray(String[]::new));

        if (request.schedule() == null || request.schedule().isNull()) {
            // Always a JSON array, never null: the column is NOT NULL with a
            // '[]' default, and a detail page that has to null-check an agenda it
            // always renders is worse than one that does not.
            event.setSchedule(JsonNodeFactory.instance.arrayNode());
        } else {
            event.setSchedule(request.schedule());
        }

        // EXTERNAL means the platform hands off to a Google Form etc., so an
        // internal venue is meaningless and a stale URL would be a dead button.
        if (request.registrationMode() == EventRegistrationMode.EXTERNAL) {
            event.setExternalRegistrationUrl(blankToNull(request.externalRegistrationUrl()));
            if (event.getExternalRegistrationUrl() == null) {
                throw new BadRequestException(
                        "A registration link is required when registration happens outside EventNest.");
            }
        } else {
            event.setExternalRegistrationUrl(null);
        }
    }

    /**
     * Rules the database also enforces, checked here so the API answers with a
     * field-level 400 instead of a 500 wrapping a CHECK violation. Both layers are
     * worth having: the database is the guarantee, the service is the courtesy.
     */
    private void validate(EventWriteRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("The end time must be after the start time.");
        }
        if (request.registrationDeadline().isAfter(request.startsAt())) {
            throw new BadRequestException("Registration must close before the event starts.");
        }
        if (request.teamSizeMin() != null && request.teamSizeMax() != null
                && request.teamSizeMax() < request.teamSizeMin()) {
            throw new BadRequestException("Maximum team size cannot be smaller than the minimum.");
        }
        if (request.prizeAmount() != null && request.prizeAmount().signum() < 0) {
            throw new BadRequestException("Prize amount cannot be negative.");
        }
    }

    /**
     * D19 in one method: a college is verified once, and only then may publish.
     * A pending or rejected organizer gets a 403 that names the actual reason -
     * the UI needs to distinguish "you are not approved yet" from "you are not
     * allowed to do this", and both would otherwise be a bare 403.
     */
    private Organizer requireApprovedOrganizer(Long organizerUserId) {
        Organizer organizer = organizerRepository.findById(organizerUserId)
                .orElseThrow(() -> new ForbiddenException(
                        "This account has no organizer profile."));
        if (!organizer.canPublish()) {
            String reason = organizer.getVerificationStatus() == OrganizerVerificationStatus.PENDING
                    ? "Your college is still being verified. You will be able to publish once an admin approves it."
                    : "Your organizer account was rejected: "
                            + (organizer.getRejectionReason() == null ? "no reason recorded."
                                    : organizer.getRejectionReason());
            throw new ForbiddenException(reason);
        }
        return organizer;
    }

    private Event owned(Long id, Long organizerUserId) {
        return eventRepository.findByIdAndOrganizerUserId(id, organizerUserId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + id));
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("That category does not exist."));
    }

    private java.util.Optional<EventStatus> parseStatus(String status) {
        try {
            return java.util.Optional.of(EventStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}