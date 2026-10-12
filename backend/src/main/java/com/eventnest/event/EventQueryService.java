package com.eventnest.event;

import com.eventnest.bookmark.BookmarkRepository;
import com.eventnest.category.Category;
import com.eventnest.category.CategoryRepository;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.common.exception.UnauthorizedException;
import com.eventnest.event.dto.EventDtos.EventDetail;
import com.eventnest.event.dto.EventDtos.EventQuery;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.event.dto.EventDtos.HomeResponse;
import com.eventnest.event.dto.EventDtos.MyRegistration;
import com.eventnest.registration.Registration;
import com.eventnest.registration.RegistrationRepository;
import com.eventnest.student.StudentProfile;
import com.eventnest.student.StudentProfileRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the events feature: browsing, search, detail and the home page.
 *
 * <p><b>Split from {@link EventCommandService}</b> because the two have genuinely
 * different shapes. Reads are filter-composition plus mapping and want to stay
 * free of write transactions; writes are state-machine transitions plus ownership
 * checks and want exactly one transaction each. Bundling them into one
 * {@code EventService} is how a service grows to 900 lines where the read path
 * and the write path never actually share a line of logic.
 *
 * <p>Reads are marked {@code @Transactional(readOnly = true)} rather than left
 * implicit. With {@code open-in-view: false} there is no session hanging around
 * after the controller returns, so an explicit transaction is what guarantees the
 * {@code EAGER} associations the mapper walks are loaded inside a session at all.
 * {@code readOnly} also lets PostgreSQL skip row locks and lets the JDBC driver
 * use a read-only connection - a real saving on a list endpoint.
 */
@Service
@RequiredArgsConstructor
public class EventQueryService {

    /** Rails are capped: a home page that can grow unbounded is one nobody scrolls past. */
    private static final int RAIL_SIZE = 6;
    private static final int HOME_CATEGORY_EVENT_LIMIT = 8;

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final CategoryRepository categoryRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RegistrationRepository registrationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final Clock clock;

    // ------------------------------------------------------------- discovery --

    /**
     * {@code GET /events} - one endpoint serving browse, search and the filter
     * panel.
     *
     * <p>Filters compose as {@code AND}, which is what a user filtering by
     * "hackathon" AND "Pune" AND "this month" expects. Fields that are null or
     * blank simply never enter the specification, so a plain browse request
     * produces a predicate that {@code ix_events_discovery} can serve directly -
     * rather than a dozen {@code AND (:x IS NULL OR ...)} clauses, which is the
     * usual way an optional-filter query ends up unable to use any index at all.
     */
    @Transactional(readOnly = true)
    public PageResponse<EventSummary> search(EventQuery query, Long viewerId) {
        if ((query.registeredRequested() || query.savedRequested()) && viewerId == null) {
            // "Only what I registered for" while signed out is a client bug; an
            // empty list would mask it behind a confusing UI.
            throw new UnauthorizedException("Sign in to filter by your registrations or saved events.");
        }

        Pageable pageable = PageRequest.of(query.pageNumber(), query.pageSize(), sortFor(query));

        var page = eventRepository.findAll(specification(query, viewerId), pageable);
        return PageResponse.from(page, e -> decorate(e, viewerId));
    }

    /**
     * {@code GET /events/{idOrSlug}} - accepts either, because a slug is readable
     * and shareable in a URL while an id is what the rest of the system uses. Both
     * are unique in the schema, so there is no ambiguity: try the numeric form
     * first, fall back to slug.
     *
     * <p><b>An unpublished event is a 404, not a 403.</b> A 403 would confirm to
     * anyone who guessed an id that the event exists - leaking a competitor's
     * unreleased event and the fact that a college is about to announce something.
     * Only the owning organizer and an admin ever get a different answer.
     */
    @Transactional(readOnly = true)
    public EventDetail detail(String idOrSlug, Long viewerId, String viewerRole) {
        Event event = findVisible(idOrSlug, viewerId, viewerRole);

        // Counted only after visibility is settled, so a 404 never inflates
        // analytics. The increment runs in its own transaction (see the repository
        // method) so failing to record a view can never fail the page render.
        eventRepository.incrementViewCount(event.getId());

        EventDetail detail = eventMapper.toDetail(event);
        if (viewerId != null) {
            Optional<Registration> mine = registrationRepository
                    .findByEventIdAndUserId(event.getId(), viewerId);
            if (mine.isPresent()) {
                detail = withMyRegistration(detail, mine.get());
            }
        }
        return detail;
    }

    /**
     * {@code GET /events/{id}/related} - same category first, then anything else
     * upcoming, capped at six.
     *
     * <p>Two buckets rather than a score across every dimension: it keeps the
     * query to one indexed lookup and, more importantly, gives a result a user can
     * explain ("these are other hackathons happening near you").
     */
    @Transactional(readOnly = true)
    public List<EventSummary> related(String idOrSlug, Long viewerId, String viewerRole) {
        Event event = findVisible(idOrSlug, viewerId, viewerRole);
        Instant now = Instant.now(clock);

        Set<Long> seen = new LinkedHashSet<>();
        seen.add(event.getId());
        List<Event> result = new ArrayList<>(RAIL_SIZE);

        eventRepository.findUpcomingInCategory(EventStatus.PUBLISHED,
                event.getCategory().getSlug(), PageRequest.of(0, RAIL_SIZE + 1)).stream()
                .filter(e -> !seen.contains(e.getId()))
                .limit(RAIL_SIZE)
                .forEach(e -> {
                    seen.add(e.getId());
                    result.add(e);
                });

        if (result.size() < RAIL_SIZE) {
            eventRepository.findUpcoming(EventStatus.PUBLISHED, now, PageRequest.of(0, 50)).stream()
                    .filter(e -> !seen.contains(e.getId()))
                    .limit(RAIL_SIZE - result.size())
                    .forEach(result::add);
        }
        return decorateAll(result, viewerId);
    }

    // ----------------------------------------------------------------- home --

    /**
     * {@code GET /events/home} - the entire home page in one round trip (rationale
     * on {@link HomeResponse}).
     *
     * <p>Each rail is an independent indexed query with its own small limit. That
     * is several queries instead of one clever one, and it is the right trade:
     * "closing soon" wants {@code ix_events_deadline}, "trending" wants
     * {@code ix_events_popular}, "near you" wants {@code ix_events_city}. One
     * query serving six different orderings would be one query using six different
     * indexes badly.
     */
    @Transactional(readOnly = true)
    public HomeResponse home(Long viewerId) {
        Instant now = Instant.now(clock);
        Pageable rail = PageRequest.of(0, RAIL_SIZE);

        List<Event> upcoming = eventRepository.findUpcoming(EventStatus.PUBLISHED, now,
                PageRequest.of(0, RAIL_SIZE * 4));

        // "Recommended" has no model behind it yet, so the honest definition is
        // "the next things happening". If a real ranking ever lands, this is the
        // one line that changes - the response shape does not.
        List<Event> recommended = upcoming.stream()
                .sorted(Comparator.comparing(Event::getStartsAt))
                .limit(RAIL_SIZE)
                .toList();

        List<Event> trending = eventRepository.findTrending(EventStatus.PUBLISHED, rail);
        List<Event> closingSoon = eventRepository.findClosingSoon(EventStatus.PUBLISHED, now, rail);

        // publishedAt can be null for an event created directly as PUBLISHED, so
        // nulls sort last rather than first - otherwise the rail would open with
        // the events we know least about.
        List<Event> newlyAdded = upcoming.stream()
                .sorted(Comparator.comparing(Event::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RAIL_SIZE)
                .toList();

        List<Event> nearYou = viewerId == null ? List.of() : nearYou(viewerId, now);

        Map<String, List<EventSummary>> byCategory = new LinkedHashMap<>();
        for (Category category : categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc()) {
            List<Event> inCategory = eventRepository.findUpcomingInCategory(
                    EventStatus.PUBLISHED, category.getSlug(),
                    PageRequest.of(0, HOME_CATEGORY_EVENT_LIMIT));
            if (!inCategory.isEmpty()) {
                byCategory.put(category.getSlug(), decorateAll(inCategory, viewerId));
            }
        }

        return new HomeResponse(
                decorateAll(recommended, viewerId),
                decorateAll(nearYou, viewerId),
                decorateAll(trending, viewerId),
                byCategory,
                decorateAll(closingSoon, viewerId),
                decorateAll(newlyAdded, viewerId));
    }

    /**
     * The viewer's city <b>or</b> state.
     *
     * <p>State alone would flood the rail with every event in Maharashtra for a
     * student in Kolhapur; city alone would show nothing whenever the profile
     * spells a place slightly differently from the event. City is the tighter
     * match, state the widening fallback, combined with OR - which is also the
     * order the {@code ix_events_city} / {@code ix_events_state} indexes support.
     */
    private List<Event> nearYou(Long viewerId, Instant now) {
        StudentProfile profile = studentProfileRepository.findById(viewerId).orElse(null);
        if (profile == null || (isBlank(profile.getCity()) && isBlank(profile.getState()))) {
            return List.of();
        }

        String city = isBlank(profile.getCity()) ? null : profile.getCity().trim().toLowerCase();
        String state = isBlank(profile.getState()) ? null : profile.getState().trim().toLowerCase();

        Specification<Event> local = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (city != null) {
                predicates.add(cb.equal(cb.lower(root.get("city")), city));
            }
            if (state != null) {
                predicates.add(cb.equal(cb.lower(root.get("state")), state));
            }
            if (predicates.isEmpty()) {
                return cb.disjunction();
            }
            return cb.and(cb.or(predicates.toArray(new Predicate[0])),
                    cb.equal(root.get("status"), EventStatus.PUBLISHED),
                    cb.greaterThan(root.get("endsAt"), now));
        };
        return eventRepository.findAll(local, PageRequest.of(0, RAIL_SIZE)).getContent();
    }

    // -------------------------------------------------------------- internals --

    /**
     * Resolves an id or slug to an event the caller may see, throwing
     * {@link NotFoundException} otherwise - including for a real but unpublished
     * event the caller has no rights over (see {@link #detail}).
     */
    private Event findVisible(String idOrSlug, Long viewerId, String viewerRole) {
        Optional<Event> found = looksNumeric(idOrSlug)
                ? eventRepository.findById(Long.valueOf(idOrSlug))
                : eventRepository.findBySlug(idOrSlug);
        if (found.isEmpty()) {
            throw notFound(idOrSlug);
        }
        Event event = found.get();
        if (event.getStatus().isVisibleToStudents()) {
            return event;
        }
        boolean owner = viewerId != null && viewerId.equals(event.getOrganizer().getUserId());
        boolean admin = "ADMIN".equals(viewerRole);
        if (owner || admin) {
            return event;
        }
        // Deliberately indistinguishable from "no such row".
        throw notFound(idOrSlug);
    }

    private NotFoundException notFound(String idOrSlug) {
        return new NotFoundException("Event not found: " + idOrSlug);
    }

    private static boolean looksNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Public entry point for other query services (the college page needs the same
     * saved/registered decoration on a list it fetched itself). Keeping it here
     * rather than duplicating the two-flag logic means there is exactly one
     * definition of what a decorated summary is.
     */
    @Transactional(readOnly = true)
    public List<EventSummary> summarize(List<Event> events, Long viewerId) {
        return decorateAll(events, viewerId);
    }

    /** Maps a page of events, then stamps on this viewer's saved/registered state. */
    private List<EventSummary> decorateAll(List<Event> events, Long viewerId) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<EventSummary> summaries = eventMapper.toSummaryList(events);
        if (viewerId == null) {
            return summaries;
        }
        Set<Long> ids = events.stream().map(Event::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> saved = new LinkedHashSet<>(bookmarkRepository.findBookmarkedEventIds(viewerId, ids));
        Set<Long> registered = new LinkedHashSet<>(registrationRepository.findRegisteredEventIds(viewerId, ids));

        List<EventSummary> decorated = new ArrayList<>(summaries.size());
        for (EventSummary summary : summaries) {
            decorated.add(summary.withViewerFlags(
                    saved.contains(summary.id()), registered.contains(summary.id())));
        }
        return decorated;
    }

    private EventSummary decorate(Event event, Long viewerId) {
        return decorateAll(List.of(event), viewerId).get(0);
    }

    private EventDetail withMyRegistration(EventDetail detail, Registration registration) {
        MyRegistration mine = new MyRegistration(
                registration.getId(),
                registration.getRegistrationNumber(),
                registration.getStatus().name(),
                registration.getCreatedAt());
        // Records are immutable, so the viewer-specific slice is rebuilt rather
        // than mutated - the same two-phase idea as EventSummary.withViewerFlags.
        return new EventDetail(detail.summary(), detail.description(), detail.addressLine(),
                detail.district(), detail.country(), detail.latitude(), detail.longitude(),
                detail.registrationMode(), detail.externalRegistrationUrl(), detail.teamSizeMin(),
                detail.teamSizeMax(), detail.prizeDescription(), detail.eligibility(), detail.rules(),
                detail.schedule(), detail.coordinatorName(), detail.coordinatorEmail(),
                detail.coordinatorPhone(), detail.bannerUrl(), detail.moderationRequired(),
                detail.publishedAt(), detail.cancelledAt(), detail.cancellationReason(),
                detail.createdAt(), detail.updatedAt(), mine);
    }

    /**
     * Sort whitelist. Callers pass a free string from the URL, so this is where an
     * unknown value becomes a sensible default instead of a 500 - and where
     * {@code Sort} is built from our own fixed set rather than from raw request
     * input, which is exactly the shape "SQL injection via ORDER BY" takes.
     *
     * <p>{@code RELEVANCE} is accepted but never used to build a {@link Sort}:
     * ordering by {@code similarity()} would have to live in the Criteria query,
     * which Spring Data overwrites from the {@code Pageable}'s sort. Real ranking
     * means a tsvector column or a native query - a deliberate future enhancement,
     * not something to fake by silently returning a different order.
     */
    private Sort sortFor(EventQuery query) {
        return switch (query.sort() == null ? "" : query.sort().toUpperCase()) {
            case "DEADLINE" -> Sort.by(Sort.Direction.ASC, "registrationDeadline");
            case "POPULAR" -> Sort.by(Sort.Direction.DESC, "registrationCount");
            case "NEWEST" -> Sort.by(Sort.Direction.DESC, "publishedAt");
            default -> Sort.by(Sort.Direction.ASC, "startsAt");
        };
    }

    private Specification<Event> specification(EventQuery query, Long viewerId) {
        Specification<Event> spec = published();
        Instant now = Instant.now(clock);

        if (!query.includeClosedRequested()) {
            spec = spec.and((root, cq, cb) -> cb.greaterThan(root.get("endsAt"), now));
        }
        if (!isBlank(query.q())) {
            String term = "%" + query.q().trim().toLowerCase() + "%";
            // ILIKE against the generated search_text column, which is what the
            // GIN trigram index ix_events_search_trgm is built on.
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("searchText")), term));
        }
        if (!isBlank(query.category())) {
            List<String> slugs = List.of(query.category().split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!slugs.isEmpty()) {
                spec = spec.and((root, cq, cb) -> root.get("category").get("slug").in(slugs));
            }
        }
        if (query.collegeId() != null) {
            Long collegeId = query.collegeId();
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("college").get("id"), collegeId));
        }
        if (!isBlank(query.city())) {
            String city = query.city().trim().toLowerCase();
            spec = spec.and((root, cq, cb) -> cb.equal(cb.lower(root.get("city")), city));
        }
        if (!isBlank(query.district())) {
            String district = query.district().trim().toLowerCase();
            spec = spec.and((root, cq, cb) -> cb.equal(cb.lower(root.get("district")), district));
        }
        if (!isBlank(query.state())) {
            String state = query.state().trim().toLowerCase();
            spec = spec.and((root, cq, cb) -> cb.equal(cb.lower(root.get("state")), state));
        }
        if (query.mode() != null) {
            EventMode mode = query.mode();
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("mode"), mode));
        }
        if (query.from() != null) {
            Instant from = query.from();
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from));
        }
        if (query.to() != null) {
            Instant to = query.to();
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), to));
        }

        // The two viewer-scoped filters. Resolving the id set up front and pushing
        // it down as `id IN (...)` keeps the page count correct: filtering a fetched
        // page in Java would report a total that includes rows the caller cannot
        // see, and the UI would show "1 of 12" on a page with three items.
        if (query.registeredRequested()) {
            Set<Long> registeredIds = new LinkedHashSet<>(registrationRepository.findAllRegisteredEventIds(viewerId));
            spec = spec.and((root, cq, cb) -> root.get("id").in(registeredIds));
        }
        if (query.savedRequested()) {
            Set<Long> savedIds = new LinkedHashSet<>(bookmarkRepository.findAllBookmarkedEventIds(viewerId));
            spec = spec.and((root, cq, cb) -> root.get("id").in(savedIds));
        }
        return spec;
    }

    private Specification<Event> published() {
        return (root, cq, cb) -> cb.equal(root.get("status"), EventStatus.PUBLISHED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}