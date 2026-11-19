package com.hkust.marketplace.models;

/**
 * Buyer account: browses listings and makes offers, borrow requests and claims.
 * Buyer-specific state moves here once {@link User} becomes abstract (ADR-0001).
 */
public class Buyer extends User {
    private String residenceHall;

    public Buyer() {
        super();
    }

    public Buyer(String username, String fullName, String passwordHash, UserRole role) {
        super(username, fullName, passwordHash, role);
    }

    public String getResidenceHall() { return residenceHall; }
    public void setResidenceHall(String residenceHall) { this.residenceHall = residenceHall; }
}
