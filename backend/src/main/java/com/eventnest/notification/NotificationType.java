package com.eventnest.notification;

/**
 * Mirrors {@code ck_notifications_type} in migration V7.
 *
 * <p>The list is an allowlist rather than free text: a notification type is a
 * contract with the frontend, which maps it to an icon and an action URL. Allowing
 * arbitrary strings would let a new backend code ship a notification the UI
 * renders as an unstyled blank row.
 */
public enum NotificationType {
    DEADLINE_REMINDER,
    EVENT_STARTING,
    NEW_EVENT_MATCH,
    REGISTRATION_CONFIRMED,
    REGISTRATION_CANCELLED,
    NEW_REGISTRATION,
    EVENT_CANCELLED,
    APPROVAL_DECISION,
    REPORT_RESOLVED,
    SYSTEM
}
