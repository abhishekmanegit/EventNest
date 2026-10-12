package com.eventnest.notification;

import java.time.Instant;

/**
 * Wire shapes for the bell and the inbox (ARCHITECTURE.md §7.3).
 *
 * <p>{@code eventId} and {@code actionUrl} travel together on purpose: the
 * frontend never constructs a link from an id, so a notification can only ever
 * point where the backend told it to. That also means a future deep-link scheme
 * change is one backend edit, not a release of every installed client.
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationItem(
            Long id,
            NotificationType type,
            String title,
            String body,
            String actionUrl,
            Long eventId,
            boolean read,
            Instant readAt,
            Instant createdAt) {
    }

    /** The bell badge - a number, not a list, so the poll is a single column. */
    public record UnreadCount(long count) {
    }
}
