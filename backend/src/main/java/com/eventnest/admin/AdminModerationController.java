package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminEventResult;
import com.eventnest.admin.AdminDtos.AdminEventRow;
import com.eventnest.admin.AdminDtos.EventModerationRequest;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.report.ReportDtos.ReportSummary;
import com.eventnest.report.ReportStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The after-the-fact queues: events and reports (ARCHITECTURE.md §7.5).
 *
 * <p>These two are the enforcement half of decision D19 and they are presented
 * side by side on purpose - a report about a live event is usually acted on here
 * in two calls (dismiss-or-resolve the report, maybe take down the event). Both
 * read from the same services the admin UI sits on, so there is no second,
 * shadow implementation of moderation to keep honest.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final AdminModerationService moderationService;

    public AdminModerationController(AdminModerationService moderationService) {
        this.moderationService = moderationService;
    }

    // ----------------------------------------------------------------- events --

    @GetMapping("/events")
    public PageResponse<AdminEventRow> events(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String categoryId,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return moderationService.events(status, categoryId, q, page, Math.min(Math.max(1, size), 100));
    }

    /** APPROVE | REJECT | REMOVE | FEATURE | UNFEATURE - see service javadoc. */
    @PatchMapping("/events/{id}/moderation")
    public AdminEventResult moderate(@PathVariable Long id,
                                     @Valid @RequestBody EventModerationRequest request) {
        return moderationService.moderate(id, request);
    }

    // ----------------------------------------------------------------- reports --

    @GetMapping("/reports")
    public PageResponse<ReportSummary> reports(@RequestParam(required = false) ReportStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return moderationService.reports(status, page, Math.min(Math.max(1, size), 100));
    }

    /** RESOLVE | DISMISS, plus an optional event takedown in the same transaction. */
    @PatchMapping("/reports/{id}")
    public ReportSummary decideReport(@PathVariable Long id,
                                      @Valid @RequestBody AdminModerationService.ReportDecisionRequest request) {
        return moderationService.decideReport(id, request.action(), request.note(), request.takedown());
    }
}