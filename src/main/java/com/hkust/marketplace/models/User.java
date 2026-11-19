package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base for every marketplace account. Carries only the identity and credential
 * state common to Buyer, Seller and Admin, plus the role discriminator used for portal
 * routing (ADR-0001). Role-specific state lives on the subtypes.
 * Simple in-memory user model (no persistence).
 * TODO: Replace with real entity + repository when backend is added.
 */
public abstract class User {
    private String username;
    private String fullName;
    private String passwordHash; // BCrypt hash produced by PasswordHasher; plaintext is never stored
    private final UserRole role;
    // Activity 1: isActive
    private boolean active = true;
    // Activity 1: registeredDate
    private LocalDateTime createdAt;

    protected User(String username, String fullName, String password, UserRole role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = password; // Callers pass an already-hashed password (see PasswordHasher)
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserRole getRole() { return role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return fullName + " (@" + username + ")";
    }
}
