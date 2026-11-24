package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for a listing. Concrete kinds are {@link SaleListing},
 * {@link LendListing} and {@link GiveawayListing}; each supplies its type and
 * price display. TODO: Persist to database and add photo storage when backend is ready.
 */
public abstract class Listing {
    private static final int MAX_PHOTOS = 6;

    private String id;
    private String title;
    private String description;
    private Category category;
    private ListingStatus status;
    private Condition condition;          // New / Like New / Used / For Parts
    private Location meetupLocation;
    private final Seller seller;
    private LocalDateTime submittedDate;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private String takedownReason;
    private int viewCount;
    private boolean deleted;
    private List<String> photoPaths;

    protected Listing(Seller seller, String title, String description, Category category,
                      Condition condition, Location meetupLocation) {
        if (seller == null) {
            throw new IllegalArgumentException("Seller must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Condition must not be null");
        }
        if (meetupLocation == null) {
            throw new IllegalArgumentException("Meetup location must not be null");
        }
        this.seller = seller;
        this.title = title;
        this.description = description == null ? "" : description;
        this.category = category;
        this.condition = condition;
        this.meetupLocation = meetupLocation;
        this.status = ListingStatus.PENDING_REVIEW;
        this.submittedDate = LocalDateTime.now();
        this.viewCount = 0;
        this.photoPaths = new ArrayList<>();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public abstract ListingType getType();

    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }

    public Condition getCondition() { return condition; }
    public void setCondition(Condition condition) { this.condition = condition; }

    public Location getMeetupLocation() { return meetupLocation; }
    public void setMeetupLocation(Location meetupLocation) { this.meetupLocation = meetupLocation; }

    public Seller getSeller() { return seller; }

    /**
     * Derived from {@code seller}. Kept so {@code DealRequest} and all three portals
     * compile unchanged; do not add setters.
     */
    public String getSellerUsername() { return seller.getUsername(); }

    /**
     * Derived from {@code seller}. Kept so {@code DealRequest} and all three portals
     * compile unchanged; do not add setters.
     */
    public String getSellerFullName() { return seller.getDisplayName(); }

    public LocalDateTime getSubmittedDate() { return submittedDate; }
    /** seed and tests only */
    public void setSubmittedDate(LocalDateTime submittedDate) { this.submittedDate = submittedDate; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getTakedownReason() { return takedownReason; }
    public void setTakedownReason(String takedownReason) { this.takedownReason = takedownReason; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public List<String> getPhotoPaths() { return photoPaths; }
    /** Sets the photo paths, rejecting more than six. */
    public void setPhotoPaths(List<String> photoPaths) {
        if (photoPaths != null && photoPaths.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("A listing can have at most 6 photos");
        }
        this.photoPaths = photoPaths == null ? new ArrayList<>() : new ArrayList<>(photoPaths);
    }

    /** Adds one photo path, rejecting blanks and a seventh photo. */
    public void addPhoto(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Photo path must not be blank");
        }
        if (photoPaths.size() == MAX_PHOTOS) {
            throw new IllegalStateException("A listing can have at most 6 photos");
        }
        photoPaths.add(path);
    }

    public abstract String getPriceDisplay();

    public String getTypeDisplay() {
        return switch (getType()) {
            case SELL -> "For Sale";
            case LEND -> "For Lending";
            case GIVEAWAY -> "Giveaway";
        };
    }
}
