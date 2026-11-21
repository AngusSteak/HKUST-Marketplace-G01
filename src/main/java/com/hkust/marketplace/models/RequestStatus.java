package com.hkust.marketplace.models;

/** Lifecycle of a deal request from submit to its final outcome. */
public enum RequestStatus {
    PENDING,
    COUNTERED,
    ACCEPTED,
    DECLINED
}
