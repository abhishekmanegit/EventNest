package com.eventnest.notification;

import com.eventnest.event.Event;
import com.eventnest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An in-app notification. Not an email: delivery channels are a later phase and
 * the row is the source of truth regardless of how many there eventually are.
 *
 * <p><b>Not an {@link com.eventnest.common.entity.Auditable} subclass</b> - the
 * table has {@code created_at} and no {@code updated_at}, because the only mutable
 * state is {@code is_read} and re-stamping a row every time someone glances at the
 * bell would destroy the one fact the table exists to record: when it was created.
 *
 * <p>{@code eventId} is a nullable foreign key rather than a generic
 * {@code (entity_type, entity_id)} pair - see the rationale at the top of
 * migration V7. The short version is that a polymorphic reference cannot be
 * cascade-deleted or referentially checked, so it would rot silently.
 *
 * <p>{@code dedupeKey} is what makes a retried or duplicated job safe: the
 * database's {@code UNIQUE (dedupe_key)} makes "send this twice" impossible
 * rather than merely unlikely.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    /**
     * Deliberately lazy and nullable: a notification about a new feature has no
     * event, and walking this association eagerly would cost a join on every row
     * of the bell menu for an association most rows never use.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_notifications_event"))
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    /**
     * {@code null} for notifications that may legitimately repeat (a per-user
     * welcome, say). In PostgreSQL a UNIQUE constraint ignores NULLs, so
     * "no dedupe key" and "dedupe on this key" coexist without a second column.
     */
    @Column(name = "dedupe_key", length = 160)
    private String dedupeKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markRead(Instant when) {
        this.read = true;
        this.readAt = when;
    }
}
