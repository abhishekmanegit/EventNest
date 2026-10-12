package com.eventnest.organizer;

import com.eventnest.registration.RegistrationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Roster wire shapes (ARCHITECTURE.md §7.4).
 *
 * <p>{@code course} and {@code graduationYear} are flattened onto the row rather
 * than nested as a profile object: the roster is a table, every column in a
 * table is flat, and making the client stitch two objects together to render one
 * row is shape-pushing, not design.
 */
public final class RosterDtos {

    private RosterDtos() {
    }

    public record RosterRow(
            Long id,
            String registrationNumber,
            RegistrationStatus status,
            Instant registeredAt,
            Instant cancelledAt,
            Instant checkedInAt,
            Long studentId,
            String studentName,
            String studentEmail,
            String course,
            Integer graduationYear,
            String notes) {
    }

    /**
     * Only ATTENDED and DISQUALIFIED are settable. CONFIRMED/CANCELLED are
     * consequences of the student's own actions, WAITLISTED is a consequence of
     * capacity - letting an organizer overwrite those would let them void a
     * registration without the student ever being told.
     */
    public record UpdateRosterRequest(
            @NotNull(message = "status is required") RosterStatus status,
            @Size(max = 500, message = "notes must be 500 characters or fewer") String notes) {
    }

    public enum RosterStatus {
        ATTENDED,
        DISQUALIFIED
    }
}
