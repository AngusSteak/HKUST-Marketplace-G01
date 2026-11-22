package com.hkust.marketplace.models;

import java.time.Duration;

public final class LendListing extends Listing {
    private final Duration lendingDuration;

    public LendListing(Seller seller, String title, String description, Category category,
                       Condition condition, Location meetupLocation, Duration lendingDuration) {
        super(seller, title, description, category, condition, meetupLocation);
        if (lendingDuration == null) {
            throw new IllegalArgumentException("Lending duration is required");
        }
        if (lendingDuration.isZero() || lendingDuration.isNegative()
                || lendingDuration.getSeconds() < 5) {
            throw new IllegalArgumentException("Lending duration must be at least 5 seconds");
        }
        this.lendingDuration = lendingDuration;
    }

    public Duration getLendingDuration() {
        return lendingDuration;
    }

    @Override
    public ListingType getType() {
        return ListingType.LEND;
    }

    @Override
    public String getPriceDisplay() {
        return "Borrow";
    }
}
