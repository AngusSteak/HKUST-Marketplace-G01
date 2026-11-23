package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.Arrays;

/**
 * Role checks for the service layer (brief: role-based access control at the API layer, not
 * only in the UI). Services call {@link #require} with the acting user before changing
 * anything, so a buyer or seller session cannot run an admin-only action even if a screen
 * offered it by mistake.
 */
public final class AccessControl {

    private AccessControl() {
    }

    /**
     * Returns {@code actor} if it is a signed-in, active account with one of {@code allowed}.
     *
     * @throws SecurityException otherwise (no actor, deactivated or deleted account, wrong role)
     */
    public static User require(User actor, UserRole... allowed) {
        if (actor == null) {
            throw new SecurityException("Not signed in");
        }
        if (!actor.isActive() || actor.isDeleted()) {
            throw new SecurityException("Account @" + actor.getUsername() + " is not active");
        }
        if (Arrays.stream(allowed).noneMatch(role -> role == actor.getRole())) {
            throw new SecurityException("@" + actor.getUsername() + " (" + actor.getRole()
                    + ") may not do this; needs " + Arrays.toString(allowed));
        }
        return actor;
    }
}
