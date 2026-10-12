package com.eventnest.event;

/**
 * Who takes responsibility for collecting a student's details.
 *
 * <p>{@link #INTERNAL} is the default because it is the only mode where the
 * platform can enforce a deadline and a capacity limit, deduplicate
 * registrations and cancel with a notification - every guarantee the product
 * makes. {@link #EXTERNAL} exists because many colleges already run a Google
 * Form they will not abandon; we link out instead of pretending to own a
 * process we cannot see. The database enforces that a URL is present
 * ({@code ck_events_external_url}) so students never get a dead button.
 */
public enum EventRegistrationMode {
    INTERNAL,
    EXTERNAL
}
