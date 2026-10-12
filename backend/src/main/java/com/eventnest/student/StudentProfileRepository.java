package com.eventnest.student;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    long countByCollegeId(Long collegeId);

    /**
     * Batch student counts for a page of colleges - one GROUP BY instead of one
     * {@code COUNT} per row, which is the difference between 3 queries and 43 when
     * the directory renders a page of 20.
     */
    @Query("SELECT sp.college.id AS collegeId, COUNT(sp) AS cnt FROM StudentProfile sp "
            + "WHERE sp.college.id IN :ids GROUP BY sp.college.id")
    List<CollegeCount> countByCollegeIds(@Param("ids") Collection<Long> ids);

    /**
     * The {@code NEW_EVENT_MATCH} fan-out. Two derived queries rather than one
     * {@code OR}: each is a plain index scan on case-insensitively comparable
     * text, and the two result sets are merged + de-duplicated by the caller.
     */
    List<StudentProfile> findByCityIgnoreCase(String city);

    List<StudentProfile> findByStateIgnoreCase(String state);

    /** Row shape for {@link #countByCollegeIds}; a projection, not a positional {@code Object[]}. */
    interface CollegeCount {
        Long getCollegeId();
        long getCnt();
    }
}
