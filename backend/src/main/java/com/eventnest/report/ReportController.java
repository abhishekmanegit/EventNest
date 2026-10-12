package com.eventnest.report;

import com.eventnest.report.ReportDtos.FileReportRequest;
import com.eventnest.report.ReportDtos.FiledReport;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filing a complaint (ARCHITECTURE.md §7.3).
 *
 * <p>Reachable by <b>any signed-in role</b>, not just students: an organizer who
 * notices a competitor running a scam event is exactly the reporter you want.
 * The filter chain's {@code anyRequest().authenticated()} covers both routes, so
 * the only gate is a valid token - deliberately frictionless, because the cost of
 * a report is what determines whether abuse gets noticed at all.
 *
 * <p>The response is {@code 201} with the report id rather than a bare 201: the
 * UI immediately shows "Report #42 filed", which gives the reporter something to
 * quote if they follow up, and makes a duplicate submission visually obvious.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/events/{eventId}/report")
    public ResponseEntity<FiledReport> reportEvent(
            @PathVariable Long eventId, @Valid @RequestBody FileReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.fileAgainstEvent(eventId, request));
    }

    @PostMapping("/colleges/{collegeId}/report")
    public ResponseEntity<FiledReport> reportCollege(
            @PathVariable Long collegeId, @Valid @RequestBody FileReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.fileAgainstCollege(collegeId, request));
    }
}
