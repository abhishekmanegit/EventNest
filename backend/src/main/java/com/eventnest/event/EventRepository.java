package com.eventnest.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for events.
 *
 * <p>Deliberately split into two kinds of method. Derived and {@code @Query}
 * methods cover cases where the shape of the result is fixed (a slug lookup, an
 * ownership check, one rail's worth of rows). Anything with optional user-supplied
 * filters goes through {@link JpaSpecificationExecutor}, because writing one
 * mega-{@code @Query} with a dozen {@code AND (:x IS NULL OR ...)} clauses is the
 * usual way to end up with a query the planner cannot use - the optional clauses
 * defeat the indexes built in migration V4. A {@code Specification} only emits the
 * predicates the caller actually asked for, so a plain "give me events" request
 * still hits {@code ix_events_discovery} directly.
 */
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findBySlug(String slug);

    /** Ownership check (authorisation layer 3): returns empty for someone else's event. */
    Optional<Event> findByIdAndOrganizerUserId(Long id, Long organizerUserId);

    boolean existsBySlug(String slug);

    /**
     * The organizer's own list. Uses {@code ix_events_organizer}
     * (organizer_id, status).
     */
    Page<Event> findByOrganizerUserIdOrderByStartsAtDesc(Long organizerUserId, Pageable pageable);

    Page<Event> findByOrganizerUserIdAndStatusOrderByStartsAtDesc(
            Long organizerUserId, EventStatus status, Pageable pageable);

    long countByOrganizerUserId(Long organizerUserId);

    long countByOrganizerUserIdAndStatus(Long organizerUserId, EventStatus status);

    /**
     * "More like this" and the per-category rail on the home page. Written as an
     * explicit query rather than a derived {@code findTop6ByStatusAndCategorySlug...}
     * because combining a {@code Top6} limit with a {@code Pageable} is ambiguous -
     * it is not obvious to a reader which of the two wins, and being obvious here
     * matters more than saving four lines.
     */
    @Query("""
            SELECT e FROM Event e
             WHERE e.status = :status
               AND e.category.slug = :categorySlug
             ORDER BY e.startsAt ASC
            """)
    List<Event> findUpcomingInCategory(@Param("status") EventStatus status,
                                       @Param("categorySlug") String categorySlug,
                                       Pageable pageable);

    /** Everything published and not yet finished, soonest first. */
    @Query("""
            SELECT e FROM Event e
             WHERE e.status = :status
               AND e.endsAt > :now
             ORDER BY e.startsAt ASC
            """)
    List<Event> findUpcoming(@Param("status") EventStatus status,
                             @Param("now") Instant now,
                             Pageable pageable);

    /**
     * "Trending" = filling up fastest, not merely popular. Ordered by remaining
     * capacity pressure and then by raw registrations, so a 20/20 event outranks a
     * 500/1000 one - which is what a student means by "everyone's going to this".
     */
    @Query("""
            SELECT e FROM Event e
             WHERE e.status = :status
             ORDER BY e.registrationCount DESC, e.startsAt ASC
            """)
    List<Event> findTrending(@Param("status") EventStatus status, Pageable pageable);

    /**
     * Home page rail "closing soon": published, still open, ordered by deadline.
     * Uses {@code ix_events_deadline} (status, registration_deadline).
     */
    @Query("""
            SELECT e FROM Event e
             WHERE e.status = :status
               AND e.registrationDeadline > :now
             ORDER BY e.registrationDeadline ASC
            """)
    List<Event> findClosingSoon(@Param("status") EventStatus status,
                                @Param("now") Instant now,
                                Pageable pageable);

    /**
     * View counter. An atomic {@code UPDATE ... SET x = x + 1} rather than
     * read-modify-write: two concurrent detail views on a plain {@code save()} would
     * both read the same value and one increment would be lost.
     *
     * <p><b>{@code PROPAGATION_REQUIRES_NEW} is load-bearing, not decoration.</b>
     * The caller ({@code EventQueryService.detail}) is annotated
     * {@code @Transactional(readOnly = true)}, and with the default {@code REQUIRED}
     * behaviour this method would simply join it - PostgreSQL then rejects the
     * write with {@code "cannot execute UPDATE in a read-only transaction"} and the
     * detail endpoint 500s. Suspending the outer transaction and starting a fresh
     * read-write one is what makes the increment possible at all.
     *
     * <p>It also gives the intended failure isolation for free: this transaction
     * commits on its own, so a failed view count cannot roll back the page render
     * that triggered it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Event e SET e.viewCount = e.viewCount + 1 WHERE e.id = :id")
    void incrementViewCount(@Param("id") Long id);

    long countByCollegeIdAndStatus(Long collegeId, EventStatus status);

    /** Batch event counts for a page of colleges - one GROUP BY, not one COUNT per row. */
    @Query("SELECT e.college.id AS collegeId, COUNT(e) AS cnt FROM Event e "
            + "WHERE e.college.id IN :ids AND e.status = :status GROUP BY e.college.id")
    List<CollegeCount> countByCollegeIdsAndStatus(@Param("ids") java.util.Collection<Long> ids,
                                                  @Param("status") EventStatus status);

    /** Row shape for {@link #countByCollegeIdsAndStatus}; a projection, not {@code Object[]}. */
    interface CollegeCount {
        Long getCollegeId();
        long getCnt();
    }

    /**
     * "Events by this college" on the public college page. Serves
     * {@code ix_events_college} (status, college_id, starts_at).
     */
    Page<Event> findByCollegeIdAndStatusOrderByStartsAtAsc(Long collegeId, EventStatus status, Pageable pageable);

    /**
     * The capacity guard (decision D9): one conditional {@code UPDATE} that both
     * tests and claims the seat.
     *
     * <p>Why not {@code count()} then {@code save()}? Under concurrency two
     * requests both read {@code registrationCount = 119 / 120}, both decide there
     * is room, and both insert - the event oversells. Wrapping that in a lock
     * serialises every registration for every event. This statement needs
     * neither: PostgreSQL takes the row lock as part of the UPDATE and, in READ
     * COMMITTED, re-evaluates the WHERE clause against the *new* row version
     * after acquiring it. The loser of the race simply matches zero rows, which
     * is exactly the signal {@code EVENT_FULL} needs.
     *
     * <p><b>No {@code PROPAGATION_REQUIRES_NEW} here, unlike
     * {@link #incrementViewCount}.</b> This increment must join the caller's
     * transaction so that if the registration INSERT then fails the whole thing
     * rolls back together - otherwise a rejected registration would permanently
     * burn a seat.
     */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            UPDATE Event e
               SET e.registrationCount = e.registrationCount + 1
             WHERE e.id = :id
               AND (e.maxParticipants IS NULL OR e.registrationCount < e.maxParticipants)
            """)
    int tryReserveSeat(@Param("id") Long id);

    /**
     * Used by the registration service to keep the counter and reality in step on
     * cancel (the decrement is clamped at zero rather than allowed to go negative
     * - a stray double-cancel must not make an event look emptier than it is).
     *
     * <p>{@code clearAutomatically = false} again, and for the same reason as
     * {@link #tryReserveSeat}: the caller still holds a managed {@code Registration}
     * it has to notify about, and dropping the persistence context mid-transaction
     * would detach it.
     */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            UPDATE Event e
               SET e.registrationCount = CASE WHEN :delta > 0
                                              THEN e.registrationCount + :delta
                                              ELSE GREATEST(0, e.registrationCount + :delta) END
             WHERE e.id = :id
            """)
    void adjustRegistrationCount(@Param("id") Long id, @Param("delta") int delta);

    long countByCollegeId(Long collegeId);

    /**
     * Scheduled {@code EVENT_STARTING} reminder fan-out: published events whose
     * start falls in the upcoming window. Uses {@code ix_events_discovery}
     * (status, ends_at), so the scan is an index range read, not a table walk.
     */
    List<Event> findByStatusAndStartsAtBetween(EventStatus status, Instant from, Instant to);

    /**
     * Scheduled {@code DEADLINE_REMINDER} fan-out: published, still-open events
     * whose registration deadline falls in the upcoming window. Pairs with
     * {@code ix_events_deadline} (status, registration_deadline).
     */
    List<Event> findByStatusAndRegistrationDeadlineBetween(
            EventStatus status, Instant from, Instant to);

    /**
     * Used by the admin category delete (deactivate first when a category is
     * still in use by non-draft events).
     */
    long countByCategoryIdAndStatusNotIn(Long categoryId, java.util.List<EventStatus> statuses);
}
