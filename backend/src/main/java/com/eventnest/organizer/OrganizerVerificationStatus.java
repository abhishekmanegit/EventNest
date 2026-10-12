package com.eventnest.organizer;

/**
 * The trust gate (decision D19: a college is verified once, here).
 *
 * <p>{@code PENDING} members can log in and complete their profile but every
 * create-event call is rejected with 403 ORGANIZER_NOT_APPROVED. That is why this
 * enum lives on {@code organizers} rather than on {@code users}: blocking the
 * login would just generate support tickets, while showing "Awaiting approval"
 * inside the app teaches the applicant what to do next.
 */
public enum OrganizerVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    public boolean canPublish() {
        return this == APPROVED;
    }
}
