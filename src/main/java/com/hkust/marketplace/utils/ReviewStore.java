package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Review;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** In-memory reviews. Seller ratings are derived from the visible ones at read time. */
public class ReviewStore {

    private final List<Review> reviews = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public List<Review> forSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    /**
     * Average over the seller's visible (non-hidden) reviews. Empty when there are no
     * visible reviews, so "no rating yet" stays distinguishable from a real 0.0 rating.
     */
    public OptionalDouble averageRating(String sellerUsername) {
        return forSeller(sellerUsername).stream()
                .mapToInt(Review::getRating)
                .average();
    }

    /** Number of visible (non-hidden) reviews behind {@link #averageRating}. */
    public int visibleCount(String sellerUsername) {
        return forSeller(sellerUsername).size();
    }

    public List<Review> all() {
        return new ArrayList<>(reviews);
    }

    public void add(Review r) {
        if (r.getId() == null) {
            r.setId("R" + idCounter.getAndIncrement());
        }
        reviews.add(r);
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
