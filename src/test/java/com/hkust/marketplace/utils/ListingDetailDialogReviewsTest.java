package com.hkust.marketplace.utils;

import com.hkust.marketplace.controllers.common.ListingDetailDialog;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ListingDetailDialogReviewsTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();

    @Test
    void allVisibleReviewsAreReturnedNewestFirstWithoutHiddenOnes() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 12, 0);
        addReview(first.plusDays(3), "Third", false);
        addReview(first.plusDays(1), "First", false);
        addReview(first.plusDays(5), "Fifth", false);
        addReview(first.plusDays(2), "Second", false);
        addReview(first.plusDays(4), "Fourth", false);
        addReview(first.plusDays(6), "Hidden newest", true);

        List<Review> visible = ListingDetailDialog.visibleSellerReviews(data.reviews(), "diana");

        assertEquals(5, visible.size(), "more than three visible reviews must all be returned");
        assertEquals(List.of("Fifth", "Fourth", "Third", "Second", "First"),
                visible.stream().map(Review::getComment).toList());
        assertFalse(visible.stream().anyMatch(Review::isHidden), "hidden reviews stay excluded");
    }

    private void addReview(LocalDateTime createdAt, String comment, boolean hidden) {
        Review review = new Review();
        review.setSellerUsername("diana");
        review.setBuyerUsername("alice");
        review.setListingTitle("Reviewed item");
        review.setRating(4);
        review.setComment(comment);
        review.setHidden(hidden);
        review.setCreatedAt(createdAt);
        data.reviews().add(review);
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
