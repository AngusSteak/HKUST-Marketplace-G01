package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Review;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedSellerRatingTest {

    @Test
    void seededCharlieRatingIsDerivedFromTheSeededReview() {
        DummyDataService store = seededStore();

        OptionalDouble average = store.reviews().averageRating("charlie");

        assertTrue(average.isPresent());
        assertEquals(5.0, average.getAsDouble());
        assertEquals(1, store.reviews().visibleCount("charlie"));
    }

    @Test
    void addingAReviewUpdatesTheDerivedAverage() {
        DummyDataService store = seededStore();
        store.reviews().add(reviewBy("bob", "charlie", 4));

        assertEquals(4.5, store.reviews().averageRating("charlie").getAsDouble());
        assertEquals(2, store.reviews().visibleCount("charlie"));
    }

    @Test
    void hidingAReviewExcludesItFromTheDerivedAverage() {
        DummyDataService store = seededStore();
        store.reviews().add(reviewBy("bob", "charlie", 4));

        reviewByRating(store, "charlie", 5).setHidden(true);

        assertEquals(4.0, store.reviews().averageRating("charlie").getAsDouble());
        assertEquals(1, store.reviews().visibleCount("charlie"));
    }

    @Test
    void hidingAllReviewsLeavesNoRatingYet() {
        DummyDataService store = seededStore();
        for (Review review : store.reviews().forSeller("charlie")) {
            review.setHidden(true);
        }

        assertFalse(store.reviews().averageRating("charlie").isPresent());
        assertEquals(0, store.reviews().visibleCount("charlie"));
    }

    @Test
    void unhidingAReviewRestoresTheDerivedAverage() {
        DummyDataService store = seededStore();
        store.reviews().add(reviewBy("bob", "charlie", 4));
        Review seededReview = reviewByRating(store, "charlie", 5);
        seededReview.setHidden(true);

        seededReview.setHidden(false);

        assertEquals(4.5, store.reviews().averageRating("charlie").getAsDouble());
        assertEquals(2, store.reviews().visibleCount("charlie"));
    }

    @Test
    void sellerWithoutReviewsHasNoRatingYet() {
        DummyDataService store = seededStore();

        assertFalse(store.reviews().averageRating("diana").isPresent());
        assertEquals(0, store.reviews().visibleCount("diana"));
    }

    private DummyDataService seededStore() {
        DummyDataService store = new DummyDataService();
        store.seedData();
        return store;
    }

    private Review reviewBy(String buyerUsername, String sellerUsername, int rating) {
        Review review = new Review();
        review.setBuyerUsername(buyerUsername);
        review.setSellerUsername(sellerUsername);
        review.setRating(rating);
        return review;
    }

    private Review reviewByRating(DummyDataService store, String sellerUsername, int rating) {
        List<Review> visible = store.reviews().forSeller(sellerUsername);
        return visible.stream()
                .filter(r -> r.getRating() == rating)
                .findFirst()
                .orElseThrow();
    }
}
