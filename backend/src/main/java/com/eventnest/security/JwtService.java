package com.eventnest.security;

import com.eventnest.config.AppProperties;
import com.eventnest.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies JWTs.
 *
 * <p>Three decisions worth being able to explain:
 *
 * <ol>
 *   <li><b>Why a JWT at all?</b> Statelessness. Every request is verified by
 *       signature alone, with no session lookup in the database, which is what
 *       lets us run more than one application instance behind a load balancer
 *       without sticky sessions.</li>
 *
 *   <li><b>Why only 15 minutes?</b> A JWT cannot be revoked before it expires
 *       (that is the trade we accepted). A short life bounds the damage of a
 *       leaked token. The frontend silently refreshes, so the user never
 *       notices; the cost is moving to opaque refresh tokens, which we did.</li>
 *
 *   <li><b>Why claim {@code type}?</b> So an access token can never be replayed
 *       against {@code /auth/refresh} or vice versa. A single-claim omission
 *       there is a real, exploitable bug in plenty of JWT implementations.</li>
 * </ol>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        byte[] secretBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256 needs >= 256 bits of key. Failing fast at startup beats
            // producing tokens that silently fail verification later.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters for HS256. "
                            + "Set the EVENTNEST_JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtlSeconds = properties.jwt().accessTokenTtl().toSeconds();
        this.clock = clock;
    }

    /**
     * {@code jti} (JWT ID) gives every token a unique id, so if we ever add a
     * revocation list or want to correlate a token with a log line we can.
     */
    public IssuedToken issueAccessToken(Long userId, UserRole role, String fullName) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_NAME, fullName)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiry, accessTokenTtlSeconds);
    }

    /**
     * @return the verified claims, or {@code null} if the token is missing,
     *         malformed, tampered with, expired, or has the wrong type.
     *         Returning null rather than throwing is deliberate: an absent or
     *         stale token on a public endpoint is normal (a logged-out visitor
     *         hitting /events), and it is not an error worth failing a request over.
     */
    public Claims parseAccessToken(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    // Verifies signature AND expiry, and enforces notBefore/audience.
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                log.warn("Rejected a token that was not an access token");
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself - it is a credential.
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
