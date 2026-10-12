package com.eventnest.bookmark;

import com.eventnest.event.Event;
import com.eventnest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * A saved event ("bookmark" is the internal name because it is unambiguous in
 * code; the UI says "Save").
 *
 * <p><b>Not an {@link com.eventnest.common.entity.Auditable} subclass</b>, even
 * though every other table has both timestamps. Migration V6 gives bookmarks a
 * creation time and no modification time - a saved event has no mutable state, so
 * an {@code updated_at} column would be a lie that only ever says "when we
 * inserted it". The entity matches the schema rather than the codebase habit.
 *
 * <p>Inserting an already-saved pair fails on {@code UNIQUE (user_id, event_id)}.
 * The service catches that and treats it as success, because double-tapping a
 * save button is not a user mistake worth an error message.
 */
@Entity
@Table(name = "bookmarks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookmarks_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookmarks_event"))
    private Event event;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
