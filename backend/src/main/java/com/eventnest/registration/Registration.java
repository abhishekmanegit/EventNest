package com.eventnest.registration;

import com.eventnest.common.entity.Auditable;
import com.eventnest.event.Event;
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
 * A student's place at an event.
 *
 * <p><b>Rows are cancelled, never deleted.</b> The organizer's roster is an audit
 * trail: who registered, when, and whether they later withdrew. Deleting on
 * cancel would also destroy the evidence needed to answer "why is my count
 * different from what I remember?", and would let a student hide a cancellation
 * history. Re-registering updates the same row rather than inserting a second
 * one - which is what the {@code UNIQUE (event_id, user_id)} constraint in
 * migration V5 forces anyway.
 *
 * <p>{@code registrationNumber} is generated once and never changes, because it
 * is what gets read out at the door and printed on a badge.
 */
@Entity
@Table(name = "registrations")
@Getter
@Setter
@NoArgsConstructor
public class Registration extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registrations_event"))
    private Event event;

    /**
     * EAGER on purpose: every roster row and every "my registrations" row needs
     * the student's name and avatar. Lazy here would be a guaranteed N+1 the
     * moment anyone renders a list.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registrations_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    @Column(name = "registration_number", nullable = false, unique = true, length = 32)
    private String registrationNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    public boolean isActive() {
        return status == RegistrationStatus.CONFIRMED || status == RegistrationStatus.WAITLISTED;
    }

    public void cancel(Instant when, String reason) {
        this.status = RegistrationStatus.CANCELLED;
        this.cancelledAt = when;
        this.cancelReason = reason;
    }
}
