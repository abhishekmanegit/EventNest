package com.eventnest.registration;

/** Mirrors {@code ck_registrations_status} in migration V5. */
public enum RegistrationStatus {
    CONFIRMED,
    CANCELLED,
    WAITLISTED,
    ATTENDED,
    DISQUALIFIED;

    public boolean countsTowardCapacity() {
        return this == CONFIRMED || this == ATTENDED;
    }
}
