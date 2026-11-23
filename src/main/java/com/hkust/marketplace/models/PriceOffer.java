package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A price offer on a Sale listing. The seller may counter with a different price. */
public class PriceOffer extends DealRequest {
    private final BigDecimal offeredPrice;
    /** Set only when the buyer accepts one of the seller's counters; {@code null} before that. */
    private BigDecimal agreedCounterPrice;

    PriceOffer(String id, Buyer buyer, Listing listing, BigDecimal offeredPrice,
               LocalDateTime submittedTime) {
        super(id, buyer, listing, ListingType.SELL, submittedTime);
        this.offeredPrice = offeredPrice;
    }

    @Override
    public DealType dealType() { return DealType.SOLD; }

    public BigDecimal getOfferedPrice() { return offeredPrice; }

    /** The counter price the buyer agreed to, or {@code null} before a counter is accepted. */
    public BigDecimal getAgreedCounterPrice() { return agreedCounterPrice; }

    /** The price that applies: the accepted counter if there is one, otherwise the offer. */
    public BigDecimal agreedPrice() {
        return agreedCounterPrice != null ? agreedCounterPrice : offeredPrice;
    }

    void agreeCounterPrice(BigDecimal counterPrice) { this.agreedCounterPrice = counterPrice; }
}
