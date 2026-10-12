package com.eventnest.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * The admin queue. Ordered oldest-first because a report is a promise that a
     * human will look, and FIFO is the only ordering that does not silently
     * deprioritise whoever complained first.
     */
    Page<Report> findAllByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
