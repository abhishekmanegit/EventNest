package com.eventnest.security;

import com.eventnest.common.util.HashUtil;
import com.eventnest.config.AppProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Database-backed, rotating refresh tokens (decision D4).
 *
 * <p>Why this table exists at all: a purely stateless JWT cannot be revoked. If
 * the only credential is a JWT, "logout" means "the client chose to stop sending
 * it" - the token itself stays valid for its remaining lifetime. That is theatre,
 * and for a platform holding real college rosters it is not good enough. Storing
 * a hash of a long-lived opaque token means logout revokes something real.
 *
 * <p>Three properties make it safe:
 * <ul>
 *   <li><b>Hash-only storage.</b> The random token goes to the client and is never
 *       persisted, so a database leak yields digests that cannot be presented to
 *       {@code /auth/refresh}.</li>
 *   <li><b>Rotation.</b> Every refresh revokes the presented token and issues a
 *       replacement. Tokens are single-use.</li>
 *   <li><b>Replay detection.</b> Presenting an already-rotated token means it was
 *       stolen - the legitimate client has moved on - so the entire family is
 *       revoked. This is the standard OAuth 2.1 token-reuse response.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    private Duration ttl() {
        return Duration.ofDays(properties.jwt().refreshTokenTtlDays());
    }

    @Transactional
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(HashUtil.sha256Hex(raw));
        token.setExpiresAt(Instant.now(clock).plus(ttl()));
        token.setUserAgent(currentUserAgent());
        token.setIpAddress(currentIp());
        repository.save(token);

        return raw;
    }

    /**
     * Validates and rotates atomically.
     *
     * @return the owning user id, or empty when the token is unknown, already
     *         used, revoked or expired
     */
    @Transactional
    public Optional<Long> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = repository.findByTokenHash(HashUtil.sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken presented = found.get();

        if (presented.isRevoked()) {
            // Replay of an already-rotated token: assume theft and kill the family.
            repository.revokeAllForUser(presented.getUserId(), Instant.now(clock));
            return Optional.empty();
        }
        if (presented.getExpiresAt().isBefore(Instant.now(clock))) {
            return Optional.empty();
        }

        // Revoke the presented token before issuing its replacement, so a crash
        // between the two steps fails closed (the user re-authenticates) rather
        // than leaving a token live whose successor we lost track of.
        presented.setRevokedAt(Instant.now(clock));
        repository.save(presented);

        String replacement = issue(presented.getUserId());
        repository.findByTokenHash(HashUtil.sha256Hex(replacement))
                .ifPresent(newToken -> {
                    presented.setReplacedById(newToken.getId());
                    repository.save(presented);
                });

        return Optional.of(presented.getUserId());
    }

    /** Real logout: the presented token can never be used again. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now(clock));
                    repository.save(token);
                });
    }

    /** Used when an admin suspends an account, so the next refresh fails immediately. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    /** Housekeeping for the scheduled cleanup job. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(30)));
    }

    private String currentUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String agent = attrs.getRequest().getHeader("User-Agent");
            return agent == null ? null : agent.substring(0, Math.min(agent.length(), 300));
        }
        return null;
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ip = attrs.getRequest().getRemoteAddr();
            return ip == null ? null : ip.substring(0, Math.min(ip.length(), 45));
        }
        return null;
    }
}