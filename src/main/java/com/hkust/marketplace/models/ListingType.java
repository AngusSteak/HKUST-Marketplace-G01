package com.hkust.marketplace.models;

/**
 * Final discriminator returned by each subclass's {@code getType()}; there is deliberately
 * no setter (see ADR-0004).
 */
public enum ListingType {
    SELL,
    LEND,
    GIVEAWAY
}
