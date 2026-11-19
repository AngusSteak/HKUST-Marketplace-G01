package com.hkust.marketplace.models;

/**
 * Buyer account: browses listings and makes offers, borrow requests and claims.
 * Carries the buyer-only residence hall; see ADR-0001.
 */
public class Buyer extends User {
    private String residenceHall;

    public Buyer(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, requireBuyerRole(role));
    }

    private static UserRole requireBuyerRole(UserRole role) {
        if (role != UserRole.BUYER) {
            throw new IllegalArgumentException("Buyer accounts require role BUYER, got " + role);
        }
        return UserRole.BUYER;
    }

    public String getResidenceHall() { return residenceHall; }
    public void setResidenceHall(String residenceHall) { this.residenceHall = residenceHall; }
}
