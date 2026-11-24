package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.persistence.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Reviews. Seller ratings are derived from the visible ones at read time. Every review is
 * saved to the {@code reviews} table when added, later changes (hidden, flagged, seller reply) by
 * {@link #save}; {@link #load()} rebuilds them at startup.
 */
public class ReviewStore {

    private final List<Review> reviews = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Database db;

    ReviewStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public synchronized List<Review> forSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    /**
     * Average over the seller's visible (non-hidden) reviews. Empty when there are no
     * visible reviews, so "no rating yet" stays distinguishable from a real 0.0 rating.
     */
    public synchronized OptionalDouble averageRating(String sellerUsername) {
        return forSeller(sellerUsername).stream()
                .mapToInt(Review::getRating)
                .average();
    }

    /** Number of visible (non-hidden) reviews behind {@link #averageRating}. */
    public synchronized int visibleCount(String sellerUsername) {
        return forSeller(sellerUsername).size();
    }

    public synchronized List<Review> all() {
        return new ArrayList<>(reviews);
    }

    public synchronized void add(Review r) {
        if (r.getId() == null) {
            r.setId("R" + idCounter.getAndIncrement());
        }
        // deal_id is UNIQUE, so the database itself refuses a second review of the same deal.
        db.update("INSERT INTO reviews (id, deal_id, seller_username, buyer_username, listing_title, rating,"
                        + " comment, seller_reply, hidden, flagged, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.getId(), r.getDealId(), r.getSellerUsername().toLowerCase(), r.getBuyerUsername().toLowerCase(),
                r.getListingTitle(), r.getRating(), r.getComment(), r.getSellerReply(), r.isHidden(), r.isFlagged(),
                r.getCreatedAt());
        // Only once saved: inside a transaction that later rolls back, the review never appears.
        db.afterCommit(() -> {
            synchronized (this) {
                reviews.add(r);
            }
        });
    }

    /** The review of this deal, or {@code null} if it has not been reviewed. */
    public synchronized Review forDeal(String dealId) {
        return reviews.stream().filter(r -> dealId != null && dealId.equals(r.getDealId())).findFirst().orElse(null);
    }

    /** Every review the buyer has written, newest first, including ones the admin hid. */
    public synchronized List<Review> byBuyer(String username) {
        return reviews.stream()
                .filter(r -> r.getBuyerUsername().equalsIgnoreCase(username))
                .sorted(java.util.Comparator.comparing(Review::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** Saves a review's changeable fields: hidden (admin), flagged (seller) and the seller's reply. */
    public synchronized void save(Review r) {
        if (!reviews.contains(r)) {
            throw new IllegalArgumentException("Unknown review: " + r.getId());
        }
        db.update("UPDATE reviews SET hidden = ?, seller_reply = ?, flagged = ? WHERE id = ?",
                r.isHidden(), r.getSellerReply(), r.isFlagged(), r.getId());
    }

    /** Rebuilds the saved reviews in the order added and resumes the {@code R} id counter. */
    synchronized void load() {
        db.query("SELECT * FROM reviews ORDER BY CAST(substr(id, 2) AS INTEGER)", row -> {
            Review r = new Review();
            r.setId(row.getString("id"));
            r.setSellerUsername(row.getString("seller_username"));
            r.setBuyerUsername(row.getString("buyer_username"));
            r.setListingTitle(row.getString("listing_title"));
            r.setRating(row.getInt("rating"));
            r.setComment(row.getString("comment"));
            r.setSellerReply(row.getString("seller_reply"));
            r.setDealId(row.getString("deal_id"));
            r.setHidden(Database.bool(row, "hidden"));
            r.setFlagged(Database.bool(row, "flagged"));
            r.setCreatedAt(Database.dateTime(row, "created_at"));
            reviews.add(r);
            idCounter.set(Math.max(idCounter.get(), Integer.parseInt(r.getId().substring(1)) + 1));
            return r;
        });
    }

    void seed() {
        Review review = new Review();
        review.setSellerUsername("charlie");
        review.setBuyerUsername("bob");
        review.setListingTitle("Old Chemistry Book");
        review.setRating(5);
        review.setComment("Very smooth transaction, book in excellent condition!");
        add(review);
    }
}
