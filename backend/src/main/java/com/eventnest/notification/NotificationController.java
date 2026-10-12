package com.eventnest.notification;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.notification.NotificationDtos.NotificationItem;
import com.eventnest.notification.NotificationDtos.UnreadCount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bell and the inbox (ARCHITECTURE.md §7.3).
 *
 * <p>Under {@code /me} but reachable by <b>every</b> authenticated role, because
 * the ORGANIZER's "a student just registered" notification is just as real as a
 * student's confirmation. The filter chain carves this path out before the
 * {@code hasRole('STUDENT')} matcher that covers the rest of {@code /me}.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    public NotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResponse<NotificationItem> inbox(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.inbox(unreadOnly, page, Math.min(Math.max(1, size), 60));
    }

    /** Cheap enough to poll: one indexed COUNT over an already-filtered range. */
    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return queryService.unreadCount();
    }

    /** 204 whether or not it was already read - marking read is idempotent. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        queryService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the <b>remaining</b> unread count rather than the number changed,
     * because that is what the caller needs: the response body is the value to
     * paint the badge with next, so a client can apply it without a second call.
     */
    @PostMapping("/read-all")
    public UnreadCount markAllRead() {
        queryService.markAllRead();
        return queryService.unreadCount();
    }
}
