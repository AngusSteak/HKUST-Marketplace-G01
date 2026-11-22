package com.hkust.marketplace.models;

public final class SaleListing extends Listing {
    private final Money price;

    public SaleListing(Seller seller, String title, String description, Category category,
                       Condition condition, Location meetupLocation, Money price) {
        super(seller, title, description, category, condition, meetupLocation);
        if (price == null) {
            throw new IllegalArgumentException("Asking price must not be null");
        }
        this.price = price;
    }

    public Money getPrice() {
        return price;
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
