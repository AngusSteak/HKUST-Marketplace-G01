package com.hkust.marketplace.models;

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
