package com.eventnest.college;

/**
 * Drives the "verified" badge students see on event cards.
 *
 * <p>Separate from {@code organizers.verification_status} on purpose: the badge
 * is about the institution being real, the organizer status is about one person's
 * authority to publish. A verified college can still have a pending organizer.
 */
public enum CollegeVerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
