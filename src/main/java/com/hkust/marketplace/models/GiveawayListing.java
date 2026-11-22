package com.hkust.marketplace.models;

public final class GiveawayListing extends Listing {
    public GiveawayListing(Seller seller, String title, String description, Category category,
                           Condition condition, Location meetupLocation) {
        super(seller, title, description, category, condition, meetupLocation);
    }

    @Override
    public ListingType getType() {
        return ListingType.GIVEAWAY;
    }

    @Override
    public String getPriceDisplay() {
        return "Free";
    }
}
