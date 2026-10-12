package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminEventResult;
import com.eventnest.admin.AdminDtos.AdminEventRow;
import com.eventnest.admin.AdminDtos.EventModerationRequest;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.event.Event;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import com.eventnest.notification.Notification;
import com.eventnest.notification.NotificationRepository;
import com.eventnest.notification.NotificationType;
import com.eventnest.report.ReportDtos.ReportSummary;
import com.eventnest.report.ReportService;
import com.eventnest.report.ReportStatus;
import com.eventnest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * After-the-fact moderation: the event queue, the report queue, and takedown
 * (ARCHITECTURE.md §7.5, decision D19).
 *
 * <p>The asymmetry in the action set is the design. {@code APPROVE}/{@code REJECT}
 * only exist for colleges that opted into {@code moderation_required} - for
 * everyone else there is nothing to approve, because there is no gate. {@code
 * REMOVE} is available for <b>every</b> event, because takedown is the control
 * that has to work in front of a scam that is already live. A product that can
 * only stop things before they appear cannot stop anything.
 */
@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final EventRepository eventRepository;
    private final ReportService reportService;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    // ------------------------------------------------------------- event queue --

    @Transactional(readOnly = true)
    public PageResponse<AdminEventRow> events(String status, String categoryId, String q,
                                              int page, int size) {
        Page<Event> rows = eventRepository.findAll(eventSpec(status, categoryId, q),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(rows, AdminModerationService::toRow);
    }

    @Transactional
    public AdminEventResult moderate(Long eventId, EventModerationRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        String action = request.action() == null
                ? "" : request.action().trim().toUpperCase(Locale.ROOT);

        switch (action) {
            case "APPROVE" -> approve(event);
            case "REJECT" -> reject(event, request.reason());
            case "REMOVE" -> remove(event, request.reason());
            case "FEATURE" -> event.setFeatured(!event.isFeatured());
            case "UNFEATURE" -> event.setFeatured(false);
            default -> throw new BadRequestException(
                    "action must be APPROVE, REJECT, REMOVE, FEATURE or UNFEATURE");
        }

        Event saved = eventRepository.save(event);
        return new AdminEventResult(saved.getId(), saved.getStatus().name(), saved.isFeatured());
    }

    private void approve(Event event) {
        if (!event.isModerationRequired()) {
            throw new BadRequestException(
                    "This event is not in a moderated college, so there is nothing to approve");
        }
        if (!EventStatus.PENDING_REVIEW.canTransitionTo(EventStatus.PUBLISHED)) {
            throw new BadRequestException("Event is " + event.getStatus() + ", not pending review");
        }
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now(clock));
        notifyOrganizer(event, "Your event has been approved",
                "“" + event.getTitle() + "” is now live in discovery.");
    }

    private void reject(Event event, String reason) {
        if (!event.isModerationRequired()) {
            throw new BadRequestException(
                    "This event is not in a moderated college, so there is nothing to reject");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection must say why");
        }
        event.setStatus(EventStatus.REJECTED);
        event.setModerationReason(reason.trim());
        notifyOrganizer(event, "Your event was not approved", reason.trim());
    }

    /**
     * Takedown. Cancelling rather than deleting, for the same reason the app
     * never hard-deletes a registration: students may already hold links, may
     * already have been told the event is on, and "it vanished with no record"
     * is a worse answer than "it was cancelled".
     *
     * <p>The organizer is notified, because a takedown they never hear about is
     * an organizer who republishes the same event tomorrow.
     */
    private void remove(Event event, String reason) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return;
        }
        event.setStatus(EventStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            event.setCancellationReason(reason.trim());
            event.setCancelledAt(Instant.now(clock));
        }
        notifyOrganizer(event, "Your event was removed",
                reason == null || reason.isBlank() ? "Removed by EventNest moderation." : reason.trim());
    }

    // ---------------------------------------------------------- report queue --

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> reports(ReportStatus status, int page, int size) {
        return reportService.queue(status, page, size);
    }

    /**
     * Resolve or dismiss, optionally taking the reported event down in the same
     * transaction.
     *
     * <p>{@code takedown} is a separate flag from {@code action} on purpose:
     * closing a report and removing an event are different decisions, and most
     * reports (a duplicate, a wrong category) close with the event untouched.
     * Requiring a distinct action for the rare combined case means the common
     * path cannot accidentally cancel a live event.
     */
    @Transactional
    public ReportSummary decideReport(Long reportId, String action, String note, boolean takedown) {
        ReportStatus outcome = switch (action == null ? "" : action.trim().toUpperCase(Locale.ROOT)) {
            case "RESOLVE" -> ReportStatus.RESOLVED;
            case "DISMISS" -> ReportStatus.DISMISSED;
            default -> throw new BadRequestException("action must be RESOLVE or DISMISS");
        };
        if (note == null || note.isBlank()) {
            throw new BadRequestException("A resolution note is required");
        }

        if (takedown) {
            Event target = reportService.eventOf(reportId);
            if (target == null) {
                throw new BadRequestException(
                        "This report is not about an event, so there is nothing to take down");
            }
            remove(target, "Removed after a report: " + note.trim());
            eventRepository.save(target);
        }
        return reportService.resolve(reportId, outcome, note);
    }

    // ---------------------------------------------------------------- helpers --

    private static Specification<Event> eventSpec(String status, String categoryId, String q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null && !status.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"),
                            EventStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))));
                } catch (IllegalArgumentException ex) {
                    throw new BadRequestException("Unknown status filter: " + status);
                }
            }
            if (categoryId != null && !categoryId.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("category").get("id"), Long.valueOf(categoryId.trim())));
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("categoryId must be a number");
                }
            }
            if (q != null && !q.isBlank()) {
                String needle = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), needle),
                        cb.like(cb.lower(root.<String>get("slug")), needle)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void notifyOrganizer(Event event, String title, String body) {
        User organizer = event.getOrganizer() == null ? null : event.getOrganizer().getUser();
        if (organizer == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(organizer);
        notification.setEvent(event);
        notification.setType(NotificationType.APPROVAL_DECISION);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setActionUrl("/organizer/events/" + event.getId());
        notificationRepository.save(notification);
    }

    private static AdminEventRow toRow(Event event) {
        return new AdminEventRow(
                event.getId(),
                event.getTitle(),
                event.getSlug(),
                event.getStatus().name(),
                event.isFeatured(),
                event.isModerationRequired(),
                event.getOrganizer() == null ? null : event.getOrganizer().getUserId(),
                event.getOrganizer() == null || event.getOrganizer().getUser() == null
                        ? null : event.getOrganizer().getUser().getFullName(),
                event.getOrganizer() == null || event.getOrganizer().getCollege() == null
                        ? null : event.getOrganizer().getCollege().getId(),
                event.getOrganizer() == null || event.getOrganizer().getCollege() == null
                        ? null : event.getOrganizer().getCollege().getName(),
                event.getCategory() == null ? null : event.getCategory().getName(),
                event.getStartsAt(),
                event.getPublishedAt(),
                event.getRegistrationCount(),
                event.getCreatedAt());
    }

    /** Report-moderation request: RESOLVE/DISMISS with the required note. */
    public record ReportDecisionRequest(
            @jakarta.validation.constraints.NotBlank String action,
            @jakarta.validation.constraints.NotBlank String note,
            boolean takedown) {
    }
}
