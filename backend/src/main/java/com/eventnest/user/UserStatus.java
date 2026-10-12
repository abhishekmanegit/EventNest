package com.eventnest.user;

/**
 * Account lifecycle. Note that organizer approval is NOT here - it lives on the
 * {@code organizers} table, because approval is a domain fact about publishing
 * authority, not about whether a login works.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    REJECTED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
