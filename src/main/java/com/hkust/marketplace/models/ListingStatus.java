package com.hkust.marketplace.models;

/**
 * The lifecycle status of a listing, from submission to its final state. The names follow
 * SRS Appendix A; a taken-down listing stays in the database so history still resolves it.
 */
public enum ListingStatus {
    PENDING_REVIEW,
    LIVE,
    REJECTED,
    RESERVED,
    SOLD,
    LENT,
    GIVEN,
    // SRS Appendix A calls an admin-removed listing TAKEN_DOWN. Do not rename it back.
    TAKEN_DOWN
}
