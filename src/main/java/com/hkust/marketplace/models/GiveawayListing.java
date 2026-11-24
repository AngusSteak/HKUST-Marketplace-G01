package com.hkust.marketplace.models;

/** A listing whose buyer claims it for free (SRS UC-1.4). */
public final class GiveawayListing extends Listing {
    /**
     * Creates a giveaway listing.
     *
     * @param seller the owning seller
     * @param title the listing title
     * @param description the listing description
     * @param category the category
     * @param condition the item condition
     * @param meetupLocation where the item is handed over
     */
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
