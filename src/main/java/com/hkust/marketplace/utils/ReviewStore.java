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
 * saved to the {@code reviews} table when added, later changes (hidden, seller reply) by
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
        // deal_id stays empty until the review flow (#23) links each review to its deal.
        db.update("INSERT INTO reviews (id, seller_username, buyer_username, listing_title, rating,"
                        + " comment, seller_reply, hidden, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.getId(), r.getSellerUsername().toLowerCase(), r.getBuyerUsername().toLowerCase(),
                r.getListingTitle(), r.getRating(), r.getComment(), r.getSellerReply(), r.isHidden(),
                r.getCreatedAt());
        reviews.add(r);
    }

    /** Saves a review's changeable fields: hidden (admin) and the seller's reply. */
    public synchronized void save(Review r) {
        if (!reviews.contains(r)) {
            throw new IllegalArgumentException("Unknown review: " + r.getId());
        }
        db.update("UPDATE reviews SET hidden = ?, seller_reply = ? WHERE id = ?",
                r.isHidden(), r.getSellerReply(), r.getId());
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
            r.setHidden(Database.bool(row, "hidden"));
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
