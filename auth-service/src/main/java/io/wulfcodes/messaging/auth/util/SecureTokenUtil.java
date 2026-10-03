package io.wulfcodes.messaging.auth.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque, URL-safe random tokens (used as refresh tokens).
 */
public final class SecureTokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private SecureTokenUtil() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
