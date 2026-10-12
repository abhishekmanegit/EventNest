package com.eventnest.notification;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.notification.NotificationDtos.NotificationItem;
import com.eventnest.notification.NotificationDtos.UnreadCount;
import com.eventnest.security.SecurityUtils;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of notifications: the bell badge and the inbox.
 *
 * <p>Note that <b>unread count and inbox are two separate queries</b> rather than
 * one payload carrying both. The badge polls (cheaply, and often), the inbox is
 * opened occasionally; bundling them would make the cheap frequent thing pay for
 * the expensive rare one.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationItem> inbox(boolean unreadOnly, int page, int size) {
        Long userId = SecurityUtils.currentUserId();
        Page<Notification> rows = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size))
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size));
        return PageResponse.from(rows, NotificationQueryService::toItem);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        return new UnreadCount(
                notificationRepository.countByUserIdAndReadFalse(SecurityUtils.currentUserId()));
    }

    /**
     * Scoped by {@code userId} in the query itself rather than checked afterwards:
     * "mark someone else's notification read" should be a 404 the same way
     * reading someone else's row is, and doing the check in SQL keeps it from
     * being a rule a future method can forget to apply.
     */
    @Transactional
    public void markRead(Long id) {
        notificationRepository.findByIdAndUserId(id, SecurityUtils.currentUserId())
                .ifPresent(notification -> notification.markRead(Instant.now(clock)));
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(SecurityUtils.currentUserId(), Instant.now(clock));
    }

    private static NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActionUrl(),
                notification.getEvent() == null ? null : notification.getEvent().getId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
