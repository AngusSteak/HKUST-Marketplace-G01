package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

/**
 * Pure credential-gate decision shared by the Buyer, Seller and Admin login portals.
 * The caller resolves the username to a {@link User}; this class only decides whether
 * that account may enter the requested portal. No UI or JavaFX dependency.
 *
 * <p>Check order is deliberate and matches the portals' original inline checks:
 * missing account, then wrong portal, then wrong password, then deleted account, then
 * deactivated account. Keeping the password check before the account-state checks avoids
 * revealing account state to someone who does not know the password.
 */
public final class LoginGate {

    /** The result of one login attempt; the caller maps it to a message. */
    public enum Outcome {
        OK,
        USER_NOT_FOUND,
        WRONG_PORTAL,
        WRONG_PASSWORD,
        DELETED,
        DISABLED
    }

    private LoginGate() {
    }

    /**
     * Decides whether the account may enter the portal.
     *
     * @param user         the account resolved from the entered username, or {@code null}
     * @param expectedRole the role accepted by the portal being used
     * @param rawPassword  the plaintext password entered by the user
     * @return OK, or the first check that failed
     */
    public static Outcome check(User user, UserRole expectedRole, String rawPassword) {
        if (user == null) {
            return Outcome.USER_NOT_FOUND;
        }
        if (user.getRole() != expectedRole) {
            return Outcome.WRONG_PORTAL;
        }
        if (!PasswordHasher.verify(rawPassword, user.getPasswordHash())) {
            return Outcome.WRONG_PASSWORD;
        }
        if (user.isDeleted()) {
            return Outcome.DELETED;
        }
        if (!user.isActive()) {
            return Outcome.DISABLED;
        }
        return Outcome.OK;
    }
}
