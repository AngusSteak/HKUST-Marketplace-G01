package com.hkust.marketplace.models;

/** Lifecycle of an accepted request, from active to its final outcome. */
public enum DealStatus {
    ACTIVE,
    RETURNED,
    AUTO_RETURNED,
    COMPLETED,
    CANCELLED
}
