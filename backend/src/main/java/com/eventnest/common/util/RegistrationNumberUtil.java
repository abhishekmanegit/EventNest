package com.eventnest.common.util;

import java.security.SecureRandom;

/**
 * Generates the human-readable registration number an organizer hands out on the
 * gate (or scans with a QR code in a later phase).
 *
 * <p>Design notes worth knowing:
 * <ul>
 *   <li>Uses {@link SecureRandom}, not {@code Random}. This value is a
 *       capability token in the future check-in flow - a predictable one could be
 *       guessed to walk into someone else's event.</li>
 *   <li>The alphabet is Crockford-style: no I, L, O, U, 0 or 1. These values get
 *       read aloud across a noisy registration desk and retyped by hand, so
 *       look-alike characters cause real errors ("is that an O or a zero?").</li>
 *   <li>~40 bits of entropy from 32 characters. Long enough that guessing is
 *       hopeless, short enough to read out over the phone.</li>
 * </ul>
 */
public final class RegistrationNumberUtil {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RegistrationNumberUtil() {
    }

    /** e.g. {@code CP-7QK3M2X9QD} */
    public static String generate() {
        StringBuilder builder = new StringBuilder("CP-");
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /**
     * Registration numbers come from a 32-symbol alphabet, which is far too small
     * to collide by birthday paradox in any realistic event - but the column is
     * still UNIQUE because "practically impossible" is not "impossible", and a
     * duplicate here would surface as a 500 on a student's registration. The
     * caller retries on DataIntegrityViolationException.
     */
    public static boolean isValidFormat(String value) {
        if (value == null || !value.startsWith("CP-") || value.length() != LENGTH + 3) {
            return false;
        }
        for (int i = 3; i < value.length(); i++) {
            if (new String(ALPHABET).indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
