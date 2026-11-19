package com.hkust.marketplace.models;

/**
 * Seller account: posts Sale, Lend and Giveaway listings and replies to reviews.
 * Carries the seller-only bio and preferred meet-up location; see ADR-0001.
 */
public class Seller extends User {
    private String bio;
    private String preferredMeetup;

    public Seller(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, requireSellerRole(role));
    }

    private static UserRole requireSellerRole(UserRole role) {
        if (role != UserRole.SELLER) {
            throw new IllegalArgumentException("Seller accounts require role SELLER, got " + role);
        }
        return UserRole.SELLER;
    }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPreferredMeetup() { return preferredMeetup; }
    public void setPreferredMeetup(String preferredMeetup) { this.preferredMeetup = preferredMeetup; }
}
