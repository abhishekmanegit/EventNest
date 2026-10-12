package com.eventnest.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Adds {@code created_at} / {@code updated_at} to every table that has them.
 *
 * <p>The JPA annotations here and the {@code set_updated_at()} PostgreSQL trigger
 * in migration V1 are intentionally redundant. Application writes get timestamps
 * from Hibernate; anything else that touches a row (a psql session, a data fix, a
 * future analytics job) still gets correct values from the database. A timestamp
 * column that can silently go stale is worse than not having one, because
 * "when did this change?" is a question you always end up needing answered.
 *
 * <p>Enabled by {@code JpaAuditingConfig} ({@code @EnableJpaAuditing}).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
