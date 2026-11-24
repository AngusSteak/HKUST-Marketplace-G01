package com.hkust.marketplace.controllers.seller;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SellerDashboardReviewsTest {

    @Test
    void summaryShowsTheAverageAndVisibleCount() {
        assertEquals("Average rating: 4.5 · 2 reviews",
                SellerDashboardController.ratingSummary(OptionalDouble.of(4.5), 2));
    }

    @Test
    void summaryUsesSingularForOneReview() {
        assertEquals("Average rating: 5.0 · 1 review",
                SellerDashboardController.ratingSummary(OptionalDouble.of(5.0), 1));
    }

    @Test
    void summaryWithNoVisibleReviewsSaysNoRatingYet() {
        assertEquals("No rating yet · 0 reviews",
                SellerDashboardController.ratingSummary(OptionalDouble.empty(), 0));
    }
}
