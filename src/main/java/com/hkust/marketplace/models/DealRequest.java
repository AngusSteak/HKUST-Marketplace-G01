package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A buyer's request on a listing: a {@link PriceOffer}, {@link BorrowRequest} or {@link Claim}
 * (SRS class diagram). Requests are created and moved between states only by
 * {@link DealLifecycle}; screens read them.
 *
 * <p>A buyer may make several requests on the same listing over time, so this is a class
 * rather than an association class between Buyer and Listing (SRS Appendix A).
 */
public abstract class DealRequest {
    private final String id;
    private final Buyer buyer;
    private final Listing listing;
    private final LocalDateTime submittedTime;
    private RequestStatus status = RequestStatus.PENDING;

    DealRequest(String id, Buyer buyer, Listing listing, ListingType requiredType,
                LocalDateTime submittedTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.buyer = Objects.requireNonNull(buyer, "buyer");
        this.listing = Objects.requireNonNull(listing, "listing");
        this.submittedTime = Objects.requireNonNull(submittedTime, "submittedTime");
        if (listing.getType() != requiredType) {
            throw new IllegalArgumentException(getClass().getSimpleName()
                    + " requires a " + requiredType + " listing, got " + listing.getType());
        }
    }

    /** What the buyer gets if this request is accepted. */
    public abstract DealType dealType();

    public String getId() { return id; }
    public Buyer getBuyer() { return buyer; }
    public Listing getListing() { return listing; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public RequestStatus getStatus() { return status; }

    public String getBuyerUsername() { return buyer.getUsername(); }
    public String getSellerUsername() { return listing.getSellerUsername(); }

    /** Display names for historical records: a soft-deleted party reads "deleted user". */
    public String getBuyerDisplayName() { return buyer.getDisplayName(); }
    public String getSellerDisplayName() { return listing.getSeller().getDisplayName(); }

    void setStatus(RequestStatus status) { this.status = status; }
}
