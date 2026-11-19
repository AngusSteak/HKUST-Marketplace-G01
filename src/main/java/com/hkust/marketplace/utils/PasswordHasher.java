package com.hkust.marketplace.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * The single place in the application that hashes or verifies passwords.
 * Passwords are stored as BCrypt hashes; plaintext is never persisted or compared.
 */
public final class PasswordHasher {

    /** BCrypt work factor: a deliberate cost that keeps brute-force attempts expensive. */
    private static final int LOG_ROUNDS = 12;

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(LOG_ROUNDS));
    }

    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }
}
