package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.utils.SellerReviewService;
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

    @Test
    void flagButtonLabelReflectsFlaggedState() {
        assertEquals("Flag", SellerDashboardController.flagButtonLabel(false));
        assertEquals("Already flagged", SellerDashboardController.flagButtonLabel(true));
    }

    @Test
    void blankReplyMessageIsShownVerbatim() {
        assertEquals(SellerReviewService.REPLY_REQUIRED,
                SellerDashboardController.reviewActionError("reply to",
                        new IllegalArgumentException(SellerReviewService.REPLY_REQUIRED)));
    }

    @Test
    void doubleFlagMessageIsShownVerbatim() {
        assertEquals(SellerReviewService.ALREADY_FLAGGED,
                SellerDashboardController.reviewActionError("flag",
                        new IllegalStateException(SellerReviewService.ALREADY_FLAGGED)));
    }

    @Test
    void roleFailureBecomesReadableMessage() {
        assertEquals("You do not have permission to flag this review.",
                SellerDashboardController.reviewActionError("flag", new SecurityException("denied")));
    }

    @Test
    void unexpectedFailureBecomesReadableMessage() {
        assertEquals("Could not flag this review: db down",
                SellerDashboardController.reviewActionError("flag", new RuntimeException("db down")));
    }
}
