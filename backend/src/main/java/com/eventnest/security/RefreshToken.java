package com.eventnest.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single issued refresh token. Deliberately an entity with a real id, because
 * rotation needs to record <em>which</em> token replaced which - that linkage is
 * what makes replay detection possible (decision D4).
 *
 * <p>Stores only {@code token_hash}: a SHA-256 digest of the random token the
 * client holds. The token itself is never persisted, so a database dump cannot be
 * used to impersonate anyone.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when this token is rotated, pointing at its replacement. */
    @Column(name = "replaced_by_id")
    private Long replacedById;

    /** Audit trail: lets a user see and revoke their own sessions later. */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}