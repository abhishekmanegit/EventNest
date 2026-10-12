package com.eventnest.user;

import com.eventnest.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The identity row shared by every role (ARCHITECTURE.md section 4.2).
 *
 * <p>Lombok is used here for accessors only. DTOs are Java records, so the JSON
 * we send over HTTP stays explicit; entities are where the boilerplate lives.
 *
 * <p>Note there is no {@code setPasswordHash} convenience on the API side and no
 * getter exposure beyond this class: {@link #getPasswordHash()} is used by the
 * authentication flow and by nothing else, and {@code UserMapper} deliberately
 * never copies it into a DTO.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stored lowercase. The UNIQUE index is therefore case-insensitive without
     * needing a CITEXT extension, and login lookup is predictable.
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt output, e.g. {@code $2a$10$N9qo8uLOickgx2ZMRZoMye...}. The column is
     * 255 characters because BCrypt is always exactly 60 - the headroom means we
     * can move to a longer algorithm later without a migration.
     *
     * <p>Plaintext passwords are never persisted, never logged, and never
     * serialised to a response.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "avatar_url", length = 400)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }
}
