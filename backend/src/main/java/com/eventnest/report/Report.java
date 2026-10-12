package com.eventnest.report;

import com.eventnest.common.entity.Auditable;
import com.eventnest.event.Event;
import com.eventnest.college.College;
import com.eventnest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user complaining about something on the platform.
 *
 * <p>This is the enforcement half of decision D19: colleges are verified once and
 * then publish freely, so the thing standing between a scam and a student is a
 * report queue with an admin takedown button. It has to be <i>fast to file</i>
 * (one authenticated POST) and <i>certain to reach a human</i>, which is why
 * {@code status} starts at {@code OPEN} and the queue is ordered oldest-first by
 * an index rather than by relevance.
 *
 * <p><b>Three nullable target columns instead of {@code (target_type, target_id)}</b>
 * - the same argument as {@code notifications.event_id} in V7. A polymorphic pair
 * cannot carry a real foreign key, so a report could then point at a row that no
 * longer exists and the moderation queue would show a takedown target nobody can
 * open. Three nullable FKs give referential integrity and ON DELETE CASCADE for
 * the cost of two extra columns and a CHECK that at least one is set.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reports_reporter"))
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_reports_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", foreignKey = @ForeignKey(name = "fk_reports_college"))
    private College college;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", foreignKey = @ForeignKey(name = "fk_reports_target_user"))
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "details", length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by", foreignKey = @ForeignKey(name = "fk_reports_resolver"))
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Reports are immutable once filed; only an admin mutates the outcome. */
    public boolean isUntouched() {
        return this.status == ReportStatus.OPEN;
    }

    public void resolve(ReportStatus newStatus, User admin, String note, Instant when) {
        this.status = newStatus;
        this.resolvedBy = admin;
        this.resolvedAt = when;
        this.resolutionNote = note;
    }
}
