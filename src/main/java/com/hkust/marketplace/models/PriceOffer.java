package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A price offer on a Sale listing. The seller may counter with a different price. */
public class PriceOffer extends DealRequest {
    private final BigDecimal offeredPrice;
    private BigDecimal counterPrice;

    PriceOffer(String id, Buyer buyer, Listing listing, BigDecimal offeredPrice,
               LocalDateTime submittedTime) {
        super(id, buyer, listing, ListingType.SELL, submittedTime);
        this.offeredPrice = offeredPrice;
    }

    @Override
    public DealType dealType() { return DealType.SOLD; }

    public BigDecimal getOfferedPrice() { return offeredPrice; }

    /** The seller's counter price, or {@code null} if the offer was never countered. */
    public BigDecimal getCounterPrice() { return counterPrice; }

    /** The price that applies if accepted: the counter price if there is one. */
    public BigDecimal agreedPrice() {
        return counterPrice != null ? counterPrice : offeredPrice;
    }

    void setCounterPrice(BigDecimal counterPrice) { this.counterPrice = counterPrice; }
}
