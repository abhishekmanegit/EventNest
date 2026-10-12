package com.eventnest.registration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, Long>, JpaSpecificationExecutor<Registration> {

    /**
     * The pre-check that lets the API answer 409 ALREADY_REGISTERED with a
     * friendly message. Note it is <b>not</b> the concurrency guarantee - that is
     * the UNIQUE (event_id, user_id) constraint, which cannot be raced. This check
     * exists purely so the common case produces a good error instead of a raw
     * ConstraintViolationException.
     */
    Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    /**
     * Pre-check for the {@code UNIQUE (registration_number)} constraint. Roughly
     * 40 bits of entropy makes a collision astronomically unlikely, so this is a
     * cheap indexed lookup performed <em>before</em> the flush rather than a retry
     * loop around it - a constraint violation mid-transaction marks the whole
     * transaction rollback-only in JPA, which makes retrying in place impossible.
     * The constraint remains the backstop; this only keeps the impossible case
     * from becoming a 500.
     */
    boolean existsByRegistrationNumber(String registrationNumber);

    /**
     * The re-registration guard, and it is conditional for exactly the reason
     * {@code tryReserveSeat} is: a double-click on "Register" after a cancel must
     * not turn one active registration into two reserved seats. Two racing
     * requests both see a {@code CANCELLED} row; only the first one's UPDATE
     * matches, the second matches zero rows and the service returns
     * {@code 409 ALREADY_REGISTERED} - which rolls its seat reservation back.
     *
     * <p>{@code clearAutomatically = false}: the caller keeps its managed
     * {@link Registration} and calls {@code EntityManager.refresh} instead, so no
     * other state in the persistence context is dropped mid-transaction.
     */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("UPDATE Registration r SET r.status = :confirmed, r.cancelledAt = NULL, "
            + "r.cancelReason = NULL WHERE r.id = :id AND r.status = :cancelled")
    int reactivateIfCancelled(@Param("id") Long id,
                              @Param("confirmed") RegistrationStatus confirmed,
                              @Param("cancelled") RegistrationStatus cancelled);

    Page<Registration> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Registration> findByEventIdOrderByCreatedAtAsc(Long eventId, Pageable pageable);

    /**
     * "Which of these events has the viewer already registered for?" - one query
     * for a whole page, and a projection so no lazy proxy is ever touched (see the
     * same note on {@link BookmarkRepository#findBookmarkedEventIds}).
     */
    @Query("SELECT r.event.id FROM Registration r WHERE r.user.id = :userId AND r.event.id IN :eventIds")
    List<Long> findRegisteredEventIds(@Param("userId") Long userId,
                                      @Param("eventIds") Collection<Long> eventIds);

    /**
     * Every event this user has registered for, used to push the
     * {@code registered=true} filter down into SQL as {@code id IN (...)} so paging
     * counts only rows the caller can actually see.
     */
    @Query("SELECT r.event.id FROM Registration r WHERE r.user.id = :userId")
    List<Long> findAllRegisteredEventIds(@Param("userId") Long userId);

    /**
     * The reminder fan-out base set: everyone still holding a valid seat at an
     * event. {@code CONFIRMED} only - someone who cancelled should not be told
     * "your event is starting soon", and a waitlisted row is not a booking yet.
     */
    @Query("SELECT r.user.id FROM Registration r WHERE r.event.id = :eventId AND r.status = :status")
    List<Long> findUserIdsByEventIdAndStatus(@Param("eventId") Long eventId,
                                             @Param("status") RegistrationStatus status);
}
