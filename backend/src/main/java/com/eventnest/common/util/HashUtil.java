package com.eventnest.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing for refresh-token lookup.
 *
 * <p>Note the deliberate asymmetry with passwords: passwords use BCrypt (slow,
 * salted, deliberately expensive), refresh tokens use SHA-256 (fast). That is
 * correct, not an oversight - the threat models are different.
 *
 * <ul>
 *   <li><b>Password:</b> an attacker who steals the database also gets to choose
 *       the password they try. Making each guess cost 1024 rounds is the whole
 *       defence.</li>
 *   <li><b>Refresh token:</b> 256 bits of {@code SecureRandom} entropy means
 *       guessing is hopeless without the hash function being slow, and the token
 *       has to be looked up on every single refresh. We only need the digest to
 *       be collision-resistant and one-way, not slow.</li>
 * </ul>
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the Java platform; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * Length-independent comparison, so a caller cannot leak the expected token
     * one byte at a time by measuring how long the rejection took.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}