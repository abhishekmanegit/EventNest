package com.eventnest.notification;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The bell list. Sorted by recency rather than unread-first: unread-first
     * would need a composite sort no index provides, and the badge already
     * carries "how many are new" as a separate cheap count.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * The insert that makes dedupe real. {@code ON CONFLICT (dedupe_key) DO NOTHING}
     * is the whole mechanism: a retried job, a double-fired event, or two racing
     * requests all converge on the database refusing the second row. An
     * application-level {@code if (!exists)} check would have a TOCTOU window and
     * would be a second implementation of a rule the schema already states.
     *
     * <p>Native because JPA has no portable spelling of {@code ON CONFLICT}.
     *
     * <p>{@code CAST(:eventId AS bigint)} is not decoration: Hibernate binds an
     * untyped NULL for a null {@code Long} in a native query, and PostgreSQL then
     * cannot infer the column type for the INSERT. The cast keeps the parameter
     * well-typed in both branches.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications
                (user_id, event_id, type, title, body, action_url, is_read, dedupe_key, created_at)
            VALUES (:userId, CAST(:eventId AS bigint), :type, :title, :body, :actionUrl,
                    FALSE, :dedupeKey, now())
            ON CONFLICT (dedupe_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("eventId") Long eventId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("body") String body,
                       @Param("actionUrl") String actionUrl,
                       @Param("dedupeKey") String dedupeKey);

    @Modifying
    @Query("UPDATE Notification n SET n.read = TRUE, n.readAt = :now "
            + "WHERE n.user.id = :userId AND n.read = FALSE")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
