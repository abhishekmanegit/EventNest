package com.eventnest.organizer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizerRepository extends JpaRepository<Organizer, Long> {

    Optional<Organizer> findByCollegeId(Long collegeId);

    List<Organizer> findByVerificationStatus(OrganizerVerificationStatus status);

    Page<Organizer> findByVerificationStatus(OrganizerVerificationStatus status, Pageable pageable);

    long countByVerificationStatus(OrganizerVerificationStatus status);

    /**
     * EAGER fetch of college in the query.
     *
     * <p>Plain {@code findAll(pageable)} would return organizers whose {@code college}
     * is a lazy proxy; the service then touches {@code getName()} outside the
     * transaction and we get "no Session is currently in progress" - or worse, a
     * silent N+1 with one query per row. Declaring the fetch here makes the join
     * explicit and keeps the count query clean.
     */
    @Query(value = "select o from Organizer o join fetch o.college order by o.createdAt desc",
            countQuery = "select count(o) from Organizer o")
    Page<Organizer> findAllWithCollegeOrderByCreatedAtDesc(Pageable pageable);

    /**
     * The approval queue with the college already loaded. The explicit count
     * query is not decoration: a fetch join and a derived count query do not
     * always agree about the joins, and Spring Data would otherwise try to
     * derive one from a query with `join fetch` in it.
     */
    @Query(value = "select o from Organizer o join fetch o.college "
            + "where o.verificationStatus = :status order by o.createdAt desc",
            countQuery = "select count(o) from Organizer o where o.verificationStatus = :status")
    Page<Organizer> findByStatusWithCollegeOrderByCreatedAtDesc(
            @Param("status") OrganizerVerificationStatus status, Pageable pageable);

    @Query("select o from Organizer o join fetch o.college where o.userId = :userId")
    Optional<Organizer> findByUserIdWithCollege(@Param("userId") Long userId);

    @Query("select o from Organizer o join fetch o.college where o.verificationStatus = :status")
    List<Organizer> findByStatusWithCollege(@Param("status") OrganizerVerificationStatus status);
}
