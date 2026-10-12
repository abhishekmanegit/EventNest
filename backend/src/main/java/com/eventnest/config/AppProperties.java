package com.eventnest.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed binding for the {@code app.*} block of application.yml.
 *
 * <p>Why a record instead of {@code @Value("${app.jwt.secret}")} fields scattered
 * across services: one place to see every tunable, compile-time types, and
 * defaults declared next to the property name rather than in a YAML file nobody
 * reads. Spring Boot 3 binds records natively, so no setters are needed.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Brand brand,
        Jwt jwt,
        Storage storage,
        Cors cors,
        Notifications notifications,
        Moderation moderation,
        Seed seed) {

    public record Brand(
            String name,
            String tagline) {
    }

    /**
     * Access tokens are short-lived and stateless; refresh tokens are long-lived
     * and revocable. 15 minutes is short enough that a leaked access token is
     * nearly harmless, and long enough that an active user never notices a
     * refresh - the frontend does that silently in the background.
     */
    public record Jwt(
            String secret,
            String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7") int refreshTokenTtlDays) {
    }

    public record Storage(
            String localPath,
            String publicBaseUrl,
            long maxFileSizeBytes) {
    }

    /**
     * CORS is an allowlist, never "*". A wildcard would let any website on the
     * internet call our authenticated endpoints on behalf of a logged-in user.
     */
    public record Cors(List<String> allowedOrigins) {
    }

    public record Notifications(boolean enabled) {
    }

    public record Moderation(boolean defaultRequired) {
    }

    /**
     * Demo data seeding. Gated on the dev profile as well as this flag, so a
     * production deploy cannot be seeded with publicly known passwords even if
     * the flag is accidentally switched on.
     */
    public record Seed(
            boolean enabled,
            String adminEmail,
            String adminPassword,
            String organizerEmail,
            String organizerPassword,
            String studentEmail,
            String studentPassword) {
    }
}
