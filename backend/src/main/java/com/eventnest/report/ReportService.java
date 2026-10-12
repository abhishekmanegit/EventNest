package com.eventnest.report;

import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.event.Event;
import com.eventnest.event.EventRepository;
import com.eventnest.report.ReportDtos.FileReportRequest;
import com.eventnest.report.ReportDtos.FiledReport;
import com.eventnest.report.ReportDtos.ReportSummary;
import com.eventnest.security.SecurityUtils;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filing (student side) and adjudication (admin side) of reports.
 *
 * <p>Both halves live in one service because they share the invariant that
 * matters: a report is single-target. The database CHECK only guarantees
 * <i>at least one</i> target is set, so "an event and a college at once" is
 * perfectly legal to PostgreSQL and nonsense to a moderation queue - keeping the
 * rule here is what stops a future caller from producing that.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final EventRepository eventRepository;
    private final CollegeRepository collegeRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ----------------------------------------------------------------- filing --

    @Transactional
    public FiledReport fileAgainstEvent(Long eventId, FileReportRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        return file(request, event, null, null);
    }

    @Transactional
    public FiledReport fileAgainstCollege(Long collegeId, FileReportRequest request) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new NotFoundException("College not found: " + collegeId));
        return file(request, null, college, null);
    }

    private FiledReport file(FileReportRequest request, Event event, College college, User target) {
        User reporter = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Signed-in user no longer exists"));

        // Reporting yourself is always either a misclick or an attempt to
        // weaponise the queue; rejecting it here keeps it out of the admin's inbox
        // rather than relying on a moderator to notice later.
        if (target != null && Objects.equals(target.getId(), reporter.getId())) {
            throw new BadRequestException("You cannot report your own account");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setEvent(event);
        report.setCollege(college);
        report.setTargetUser(target);
        report.setReason(request.reason());
        report.setDetails(trimToNull(request.details()));
        Report saved = reportRepository.save(report);
        return new FiledReport(saved.getId(), saved.getStatus(), saved.getCreatedAt());
    }

    // ---------------------------------------------------------- adjudication --

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> queue(ReportStatus status, int page, int size) {
        Page<Report> rows = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : reportRepository.findAllByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size));
        return PageResponse.from(rows, ReportService::toSummary);
    }

    /**
     * RESOLVE and DISMISS both land here and both require a note - the note is
     * what the reporter sees, and an unexplained takedown reads as arbitrary.
     *
     * <p><b>Event removal is deliberately not done here.</b> Resolving a report
     * and removing an event are two decisions that do not always travel together
     * (a report about a college's name resolves with no event touched), so
     * coupling them would make the common case do the wrong thing to the rare one.
     */
    @Transactional
    public ReportSummary resolve(Long id, ReportStatus outcome, String note) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found: " + id));
        if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.REVIEWING) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Report " + id + " is already " + report.getStatus());
        }
        User admin = userRepository.findById(SecurityUtils.currentUserId()).orElseThrow();
        report.resolve(outcome, admin, note.trim(), Instant.now(clock));
        return toSummary(reportRepository.save(report));
    }

    /** The takedown target of a report, read independently of the queue listing. */
    @Transactional(readOnly = true)
    public Event eventOf(Long reportId) {
        return reportRepository.findById(reportId).map(Report::getEvent)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));
    }

    // ---------------------------------------------------------------- helpers --

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ReportSummary toSummary(Report report) {
        return new ReportSummary(
                report.getId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getResolutionNote(),
                report.getResolvedBy() == null ? null : report.getResolvedBy().getId(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                displayName(report.getReporter()),
                report.getEvent() == null ? null : report.getEvent().getId(),
                report.getEvent() == null ? null : report.getEvent().getTitle(),
                report.getEvent() == null ? null : report.getEvent().getSlug(),
                report.getCollege() == null ? null : report.getCollege().getId(),
                report.getCollege() == null ? null : report.getCollege().getName(),
                report.getTargetUser() == null ? null : report.getTargetUser().getId(),
                report.getTargetUser() == null ? null : displayName(report.getTargetUser()));
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : user.getEmail();
    }
}
