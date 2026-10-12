package com.eventnest.college;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.college.CollegeDtos.CollegeDetail;
import com.eventnest.college.CollegeDtos.CollegeListItem;
import com.eventnest.event.EventQueryService;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.student.StudentProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the college directory (ARCHITECTURE.md section 7.2).
 *
 * <p>The directory is what makes this a marketplace rather than a single
 * college's noticeboard, so it has to answer two questions a student actually
 * asks: "is this college real?" (verification) and "is anything happening
 * there?" (counts). Both are answered with batched queries - see
 * {@link #list}.
 */
@Service
@RequiredArgsConstructor
public class CollegeQueryService {

    private final CollegeRepository collegeRepository;
    private final EventRepository eventRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EventQueryService eventQueryService;

    /**
     * {@code GET /colleges?q=&state=&verified=&includePending=&page=}
     *
     * <p><b>Three queries regardless of page size</b>: the page of colleges, one
     * {@code GROUP BY} for published event counts, one for student counts. The
     * naive version issues two counts per row - 40 extra round trips to render a
     * directory of 20 - which is exactly the N+1 shape D2 exists to prevent, just
     * wearing a {@code COUNT} instead of a lazy collection.
     */
    @Transactional(readOnly = true)
    public PageResponse<CollegeListItem> list(String q, String state, Boolean verified,
                                              Boolean includePending, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 60);

        // A directory of unverified colleges would be a directory of anyone who
        // filled in a form. Verified-by-default is the whole point of the listing;
        // callers can pass verified=false only to reach an explicit admin-style view.
        // includePending (used by the registration dropdown, never the directory
        // page) widens the net to every row so an "Other…" college created by one
        // student is immediately selectable by the next.
        boolean wantVerified = !Boolean.TRUE.equals(includePending)
                && (verified == null || verified);
        Specification<College> spec = (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(wantVerified
                    ? cb.equal(root.get("verificationStatus"), CollegeVerificationStatus.VERIFIED)
                    : cb.isNotNull(root.get("id")));
            if (q != null && !q.isBlank()) {
                String term = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), term));
            }
            if (state != null && !state.isBlank()) {
                String value = state.trim().toLowerCase();
                predicates.add(cb.equal(cb.lower(root.get("state")), value));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var pageOfColleges = collegeRepository.findAll(spec, PageRequest.of(safePage, safeSize,
                Sort.by("name")));

        List<Long> ids = pageOfColleges.getContent().stream().map(College::getId).toList();
        Map<Long, Long> eventCounts = eventRepository
                .countByCollegeIdsAndStatus(ids, EventStatus.PUBLISHED).stream()
                .collect(HashMap::new, (m, c) -> m.put(c.getCollegeId(), c.getCnt()), HashMap::putAll);
        Map<Long, Long> studentCounts = studentProfileRepository.countByCollegeIds(ids).stream()
                .collect(HashMap::new, (m, c) -> m.put(c.getCollegeId(), c.getCnt()), HashMap::putAll);

        List<CollegeListItem> items = pageOfColleges.getContent().stream()
                .map(c -> new CollegeListItem(
                        c.getId(), c.getName(), c.getSlug(), c.getLogoUrl(), c.getCity(), c.getState(),
                        c.isVerified(),
                        eventCounts.getOrDefault(c.getId(), 0L),
                        studentCounts.getOrDefault(c.getId(), 0L)))
                .toList();
        return PageResponse.of(pageOfColleges, items);
    }

    /**
     * {@code GET /colleges/{idOrSlug}} - same id-or-slug convention as the event
     * detail endpoint, so the two pages behave identically when a URL is retyped.
     *
     * <p>An unverified college is a 404 here for the same reason an unpublished
     * event is: confirming existence is itself the leak.
     */
    @Transactional(readOnly = true)
    public CollegeDetail detail(String idOrSlug) {
        College college = findVisible(idOrSlug);
        long publishedEvents = eventRepository.countByCollegeIdAndStatus(college.getId(), EventStatus.PUBLISHED);
        return new CollegeDetail(
                college.getId(), college.getName(), college.getSlug(), college.getLogoUrl(),
                college.getWebsiteUrl(), college.getAbout(), college.getCity(), college.getDistrict(),
                college.getState(), college.getCountry(), college.isVerified(),
                publishedEvents, studentProfileRepository.countByCollegeId(college.getId()),
                college.getCreatedAt());
    }

    /**
     * {@code GET /colleges/{id}/events} - the public "what's on at this college"
     * page. Delegates the visibility rules to the event query service rather than
     * re-implementing them, so an event is shown under exactly one definition of
     * "visible" across the whole API.
     */
    @Transactional(readOnly = true)
    public List<EventSummary> events(String idOrSlug, int page, int size) {
        College college = findVisible(idOrSlug);
        var result = eventRepository.findByCollegeIdAndStatusOrderByStartsAtAsc(
                college.getId(), EventStatus.PUBLISHED,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60)));
        return eventQueryService.summarize(result.getContent(), null);
    }

    private College findVisible(String idOrSlug) {
        Optional<College> found = looksNumeric(idOrSlug)
                ? collegeRepository.findById(Long.valueOf(idOrSlug))
                : collegeRepository.findBySlug(idOrSlug);
        if (found.isEmpty() || !found.get().isVerified()) {
            throw new NotFoundException("College not found: " + idOrSlug);
        }
        return found.get();
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

}
