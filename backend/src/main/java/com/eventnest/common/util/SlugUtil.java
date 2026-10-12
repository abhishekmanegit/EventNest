package com.eventnest.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a title into a URL-safe slug: "National Level Hackathon 2027" ->
 * "national-level-hackathon-2027".
 *
 * <p>Why slugs matter here: events get shared over WhatsApp and put on posters.
 * A human-readable URL survives being retyped and stays recognisable, which a
 * numeric {@code /events/4213} does not.
 *
 * <p>This is an application-level convenience, not a security control - the
 * database UNIQUE constraint on the slug column is what guarantees uniqueness.
 * The {@link #uniqueSlug} variant exists so the service can resolve collisions
 * before the insert rather than after it.
 */
public final class SlugUtil {

    /** Reserved by the app's own routes; never generate these as event slugs. */
    private static final Set<String> RESERVED = Set.of(
            "new", "edit", "admin", "api", "dashboard", "events", "colleges",
            "login", "register", "logout", "saved", "profile");

    private SlugUtil() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "untitled";
        }
        // Decompose accents (é -> e + combining acute) then drop the combining marks.
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}+", "");

        String slug = withoutMarks.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            return "untitled";
        }
        if (RESERVED.contains(slug)) {
            return slug + "-event";
        }
        // 220 is the column width; cap here so the insert can never be truncated
        // (Postgres would raise a value-too-long error we would not catch).
        return slug.length() > 220 ? slug.substring(0, 220).replaceAll("-+$", "") : slug;
    }

    /**
     * Appends -2, -3, ... until the slug is free. The caller still needs to handle
     * the unique-constraint violation, because two concurrent creates can compute
     * the same candidate; this only makes the common case clean.
     */
    public static String uniqueSlug(String title, java.util.function.Predicate<String> exists) {
        String base = slugify(title);
        if (!exists.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 500; suffix++) {
            String candidate = base + "-" + suffix;
            if (candidate.length() <= 220 && !exists.test(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely; fall back to something guaranteed unique.
        return base + "-" + Long.toHexString(System.nanoTime());
    }
}
