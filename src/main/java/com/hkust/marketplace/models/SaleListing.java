package com.hkust.marketplace.models;

/** A listing sold for a fixed asking price (SRS UC-1.4). */
public final class SaleListing extends Listing {
    private Money price;

    /**
     * Creates a sale listing.
     *
     * @param seller the owning seller
     * @param title the listing title
     * @param description the listing description
     * @param category the category
     * @param condition the item condition
     * @param meetupLocation where the item is handed over
     * @param price the asking price
     */
    public SaleListing(Seller seller, String title, String description, Category category,
                       Condition condition, Location meetupLocation, Money price) {
        super(seller, title, description, category, condition, meetupLocation);
        setPrice(price);
    }

    public Money getPrice() {
        return price;
    }

    /** Admin edit (SRS UC-3.7); the listing keeps its type and seller. */
    public void setPrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("Asking price must not be null");
        }
        this.price = price;
    }

    @Override
    public ListingType getType() {
        return ListingType.SELL;
    }

    @Override
    public String getPriceDisplay() {
        return price.getDisplay();
    }
}
