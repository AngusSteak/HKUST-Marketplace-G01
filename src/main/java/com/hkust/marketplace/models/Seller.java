package com.hkust.marketplace.models;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Seller account: posts Sale, Lend and Giveaway listings and replies to reviews.
 * Carries the seller-only bio and preferred meet-up location; see ADR-0001.
 */
public class Seller extends User {
    private String bio;
    /** 0..* preferred meet-up locations (issue #9); the multi-select widget arrives in #31. */
    private Set<Location> preferredMeetup = EnumSet.noneOf(Location.class);

    /** Creates a seller account. */
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

    public Set<Location> getPreferredMeetup() { return Collections.unmodifiableSet(preferredMeetup); }
    public void setPreferredMeetup(Set<Location> preferredMeetup) {
        this.preferredMeetup = preferredMeetup == null || preferredMeetup.isEmpty()
                ? EnumSet.noneOf(Location.class) : EnumSet.copyOf(preferredMeetup);
    }
}
