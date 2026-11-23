package com.hkust.marketplace.models;

import java.time.Duration;

public final class LendListing extends Listing {
    private Duration lendingDuration;

    public LendListing(Seller seller, String title, String description, Category category,
                       Condition condition, Location meetupLocation, Duration lendingDuration) {
        super(seller, title, description, category, condition, meetupLocation);
        setLendingDuration(lendingDuration);
    }

    public Duration getLendingDuration() {
        return lendingDuration;
    }

    public void setLendingDuration(Duration lendingDuration) {
        this.lendingDuration = LendingRules.requireLendingDuration(lendingDuration);
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
