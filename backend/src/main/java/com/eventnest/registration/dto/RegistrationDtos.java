package com.eventnest.registration.dto;

import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.registration.RegistrationStatus;
import java.time.Instant;

/**
 * Response shapes for the student registration endpoints (ARCHITECTURE.md 7.3).
 *
 * <p>Every one of them carries the {@link EventSummary} rather than just an event
 * id. The alternative - "201, go fetch the event again" - turns a single user
 * action into two round trips, and lets the UI show a stale card next to a fresh
 * confirmation. One payload is enough to repaint the button, the counter and the
 * badge.
 */
public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    /** {@code POST /events/{id}/register} → 201. */
    public record RegistrationResult(
            String registrationNumber,
            RegistrationStatus status,
            EventSummary event) {
    }

    /**
     * {@code GET /events/{id}/registration} → 200, or 204 with no body when the
     * student has never registered. 204 rather than 404: "you have no
     * registration" is the expected answer for most visitors of an event page,
     * not a failure, and forcing the client to treat an error as data is how
     * error handlers end up swallowing real faults.
     */
    public record MyRegistrationStatus(
            String registrationNumber,
            RegistrationStatus status,
            Instant registeredAt,
            Instant cancelledAt,
            /** False once the event has started - the UI swaps Cancel for "contact the coordinator". */
            boolean cancellable) {
    }

    /** {@code GET /me/registrations} - newest first. */
    public record MyRegistrationRow(
            String registrationNumber,
            RegistrationStatus status,
            Instant registeredAt,
            Instant cancelledAt,
            EventSummary event) {
    }
}
