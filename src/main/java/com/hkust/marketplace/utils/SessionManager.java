package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;

/**
 * Simple session holder for the currently logged-in user.
 * TODO: Replace with proper JWT / session token handling when backend is added.
 */
public class SessionManager {
    private static User currentUser;

    /** Signs a user in for this session. */
    public static void login(User user) {
        currentUser = user;
    }

    /** Signs the current user out. */
    public static void logout() {
        currentUser = null;
    }

    /** The signed-in user, or {@code null}. */
    public static User getCurrentUser() {
        return currentUser;
    }

    /** Whether a user is signed in. */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /** Whether the signed-in user is a buyer. */
    public static boolean isBuyer() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.BUYER;
    }

    /** Whether the signed-in user is a seller. */
    public static boolean isSeller() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.SELLER;
    }

    /** Whether the signed-in user is an admin. */
    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.ADMIN;
    }
}
