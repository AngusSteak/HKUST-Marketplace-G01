package com.hkust.marketplace.models;

/**
 * Admin account: approves listings and manages users, listings and reports.
 * Seeded only; carries no extra state beyond {@link User} (ADR-0003).
 */
public class Admin extends User {

    public Admin() {
        super();
    }

    public Admin(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, role);
    }
}
