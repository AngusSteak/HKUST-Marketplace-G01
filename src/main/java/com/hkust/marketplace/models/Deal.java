package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An accepted {@link DealRequest} and its own lifecycle (SRS class diagram, 0..1 per request).
 *
 * <p>The buyer, seller, listing and deal type are derived through the request, so they are
 * never stored twice. Deals are created and moved between states only by {@link DealLifecycle}.
 */
public class Deal {
    private final String id;
    private final DealRequest request;
    private final LocalDateTime startDate;
    private final LocalDateTime dueDate;
    private LocalDateTime completedDate;
    private DealStatus status = DealStatus.ACTIVE;
    private Review review;

    Deal(String id, DealRequest request, LocalDateTime startDate) {
        this.id = Objects.requireNonNull(id, "id");
        this.request = Objects.requireNonNull(request, "request");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.dueDate = request instanceof BorrowRequest borrow ? borrow.agreedEnd() : null;
    }

    public String getId() { return id; }
    public DealRequest getRequest() { return request; }
    public LocalDateTime getStartDate() { return startDate; }

    /** When a borrowed item must be returned; {@code null} for sales and giveaways. */
    public LocalDateTime getDueDate() { return dueDate; }

    /** When the deal was completed, returned, auto-returned or cancelled; {@code null} while active. */
    public LocalDateTime getCompletedDate() { return completedDate; }

    public DealStatus getStatus() { return status; }

    public Buyer getBuyer() { return request.getBuyer(); }
    public Listing getListing() { return request.getListing(); }
    public String getBuyerUsername() { return request.getBuyerUsername(); }
    public String getSellerUsername() { return request.getSellerUsername(); }
    public DealType getDealType() { return request.dealType(); }

    /** The buyer's review of the seller for this deal, or {@code null} if not reviewed yet. */
    public Review getReview() { return review; }
    public boolean isReviewed() { return review != null; }

    /** Attaches the buyer's review; at most one review per completed deal (SRS UC-1.9). */
    public void setReview(Review review) {
        Objects.requireNonNull(review, "review");
        if (status != DealStatus.COMPLETED) {
            throw new IllegalStateException("Only completed deals can be reviewed");
        }
        if (this.review != null) {
            throw new IllegalStateException("Deal " + id + " already has a review");
        }
        this.review = review;
    }

    void setStatus(DealStatus status) { this.status = status; }
    void setCompletedDate(LocalDateTime completedDate) { this.completedDate = completedDate; }
}
