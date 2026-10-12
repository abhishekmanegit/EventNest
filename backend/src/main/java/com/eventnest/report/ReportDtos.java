package com.eventnest.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Report wire shapes. Filing is deliberately a small DTO: the reason enum is the
 * taxonomy, {@code details} is the free text, and the target is implied by which
 * endpoint was called. Making the client send the target would let it file a
 * report against a row it is not even looking at.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record FileReportRequest(
            @NotNull(message = "reason is required") ReportReason reason,
            @Size(max = 2000, message = "details must be 2000 characters or fewer") String details) {
    }

    public record ReportSummary(
            Long id,
            ReportReason reason,
            String details,
            ReportStatus status,
            String resolutionNote,
            Long resolvedById,
            Instant resolvedAt,
            Instant createdAt,
            String reporterName,
            Long eventId,
            String eventTitle,
            String eventSlug,
            Long collegeId,
            String collegeName,
            Long targetUserId,
            String targetUserName) {
    }

    public record ResolveReportRequest(
            @NotBlank(message = "note is required") String note) {
    }

    /** Every filed report is acknowledged with its id, so the reporter can quote it. */
    public record FiledReport(Long id, ReportStatus status, Instant createdAt) {
    }
}
