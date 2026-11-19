package com.hkust.marketplace.models;

/**
 * Seller account: posts Sale, Lend and Giveaway listings and replies to reviews.
 * Seller-specific state moves here once {@link User} becomes abstract (ADR-0001).
 */
public class Seller extends User {
    private String bio;
    private String preferredMeetup;

    public Seller() {
        super();
    }

    public Seller(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, role);
    }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPreferredMeetup() { return preferredMeetup; }
    public void setPreferredMeetup(String preferredMeetup) { this.preferredMeetup = preferredMeetup; }
}
