package com.hkust.marketplace.models;

/**
 * Admin account: approves listings and manages users, listings and reports.
 * Seeded only; carries no extra state beyond {@link User} (ADR-0003).
 */
public class Admin extends User {

    public Admin(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, requireAdminRole(role));
    }

    private static UserRole requireAdminRole(UserRole role) {
        if (role != UserRole.ADMIN) {
            throw new IllegalArgumentException("Admin accounts require role ADMIN, got " + role);
        }
        return UserRole.ADMIN;
    }
}
