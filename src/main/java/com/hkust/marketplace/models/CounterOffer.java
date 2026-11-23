package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The Seller's reply to one buyer {@link DealRequest}: a different price on a Sale listing or
 * a different lending period on a Lend listing.
 *
 * <p>A counter is its own record with its own chat card, not a field on the buyer's request, so
 * the buyer's card never changes and the counter can never be edited after it is sent. To offer
 * different terms the Seller cancels this counter and sends a new one, which leaves both cards
 * in the retained history.
 *
 * <p>Counters are created and moved between states only by {@link DealLifecycle}.
 */
public final class CounterOffer {
    private final String id;
    private final DealRequest request;
    /** Countered price for a Sale listing; {@code null} for a lending counter. */
    private final BigDecimal price;
    /** Countered period for a Lend listing; {@code null} for a price counter. */
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final LocalDateTime sentTime;
    private CounterStatus status = CounterStatus.OFFERED;

    CounterOffer(String id, DealRequest request, BigDecimal price,
                 LocalDateTime startDate, LocalDateTime endDate, LocalDateTime sentTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.request = Objects.requireNonNull(request, "request");
        this.price = price;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sentTime = Objects.requireNonNull(sentTime, "sentTime");
    }

    public String getId() { return id; }

    /** The buyer's request this counter replies to. */
    public DealRequest getRequest() { return request; }

    public Listing getListing() { return request.getListing(); }
    public Buyer getBuyer() { return request.getBuyer(); }
    public String getBuyerUsername() { return request.getBuyerUsername(); }
    public String getSellerUsername() { return request.getSellerUsername(); }
    public DealType getDealType() { return request.dealType(); }

    public BigDecimal getPrice() { return price; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public LocalDateTime getSentTime() { return sentTime; }
    public CounterStatus getStatus() { return status; }

    void setStatus(CounterStatus status) { this.status = status; }
}
