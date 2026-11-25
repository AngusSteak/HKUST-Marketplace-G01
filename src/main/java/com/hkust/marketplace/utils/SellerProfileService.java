package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A seller's public profile (brief 1.10, SRS UC-1.9 postconditions): who they are, their
 * average rating and reviews, and their listings a buyer can act on. Read-only; it only
 * gathers what other users are allowed to see.
 */
public final class SellerProfileService {

    /** What a seller's profile page shows. Hidden reviews and unavailable listings are left out. */
    public record SellerProfile(Seller seller, OptionalDouble averageRating, List<Review> reviews,
                                List<Listing> liveListings) {

        public SellerProfile {
            reviews = List.copyOf(reviews);
            liveListings = List.copyOf(liveListings);
        }

        /** Whether the account was deleted; its profile then shows only past reviews. */
        public boolean deleted() {
            return seller.isDeleted();
        }
    }

    private final DummyDataService data;

    public SellerProfileService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * The public profile of a seller: the visible reviews newest first (the same ones the average
     * is computed from) and the listings that are live and browsable now.
     *
     * @throws IllegalArgumentException if {@code username} is not a seller
     */
    public SellerProfile profile(String username) {
        if (!(data.users().find(username) instanceof Seller seller)) {
            throw new IllegalArgumentException("No seller called @" + username);
        }
        List<Review> reviews = data.reviews().forSeller(seller.getUsername()).stream()
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .toList();
        List<Listing> live = data.listings().bySeller(seller.getUsername()).stream()
                .filter(ListingSearch::isBrowsable)
                .sorted(Comparator.comparing(ListingSearch::postedDate).reversed())
                .toList();
        return new SellerProfile(seller, data.reviews().averageRating(seller.getUsername()), reviews, live);
    }
}
