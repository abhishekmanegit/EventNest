package com.eventnest.college;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CollegeRepository extends JpaRepository<College, Long>,
        JpaSpecificationExecutor<College> {

    Optional<College> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<College> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Distinct state/city pairs for the location filter.
     *
     * <p>Grouped rather than a bare {@code SELECT DISTINCT c.state, c.city} because
     * two colleges in the same city would otherwise produce two identical filter
     * options - the filter is a set of places, not a set of rows.
     *
     * <p>Only verified colleges contribute: offering a filter option for a place
     * where nothing can actually be found is a worse experience than not offering
     * it at all.
     */
    @Query("""
            SELECT new com.eventnest.college.CollegeLocation(c.state, c.city)
              FROM College c
             WHERE c.verificationStatus = com.eventnest.college.CollegeVerificationStatus.VERIFIED
               AND c.city IS NOT NULL
             GROUP BY c.state, c.city
             ORDER BY c.state, c.city
            """)
    List<CollegeLocation> findDistinctLocations();
}
