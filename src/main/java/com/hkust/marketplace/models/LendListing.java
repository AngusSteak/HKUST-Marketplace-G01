package com.hkust.marketplace.models;

import java.time.Duration;

/** A listing lent for a fixed period (SRS UC-1.4). */
public final class LendListing extends Listing {
    private Duration lendingDuration;

    /**
     * Creates a lend listing.
     *
     * @param seller the owning seller
     * @param title the listing title
     * @param description the listing description
     * @param category the category
     * @param condition the item condition
     * @param meetupLocation where the item is handed over
     * @param lendingDuration how long the item may be borrowed
     */
    public LendListing(Seller seller, String title, String description, Category category,
                       Condition condition, Location meetupLocation, Duration lendingDuration) {
        super(seller, title, description, category, condition, meetupLocation);
        setLendingDuration(lendingDuration);
    }

    public Duration getLendingDuration() {
        return lendingDuration;
    }

    /** Admin edit (SRS UC-3.7); the listing keeps its type and seller. */
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
