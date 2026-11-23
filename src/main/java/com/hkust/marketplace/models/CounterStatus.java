package com.hkust.marketplace.models;

/** Lifecycle of one seller counter from send to its final outcome. */
public enum CounterStatus {
    /** Sent by the seller and waiting for the buyer's answer. */
    OFFERED,
    ACCEPTED,
    DECLINED,
    /** Withdrawn by the seller; the buyer's request returns to pending. */
    CANCELLED
}
