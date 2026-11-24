package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * A buyer's review of one completed deal: a 1-5 star rating, a comment, an optional seller
 * reply and the moderation flags (SRS UC-1.7, UC-2.6, UC-3.6).
 */
public class Review {
    private String id;
    private String sellerUsername;
    private String buyerUsername;
    private String listingTitle;
    private int rating; // 1-5
    private String comment;
    private String sellerReply;
    /** The completed deal this review is about (one review per deal); {@code null} for seeded demo reviews. */
    private String dealId;
    private boolean hidden; // admin can hide
    private boolean flagged; // seller asks the admin to review it
    private LocalDateTime createdAt;

    /** Creates an empty, visible review stamped now, used when loading rows. */
    public Review() {
        this.createdAt = LocalDateTime.now();
        this.hidden = false;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getBuyerUsername() { return buyerUsername; }
    public void setBuyerUsername(String buyerUsername) { this.buyerUsername = buyerUsername; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getDealId() { return dealId; }
    public void setDealId(String dealId) { this.dealId = dealId; }

    public String getSellerReply() { return sellerReply; }
    public void setSellerReply(String sellerReply) { this.sellerReply = sellerReply; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
