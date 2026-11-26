package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review and report edge cases (SRS UC-1.9, UC-1.10, UC-2.9, UC-3.8). Seed data: D1 is alice's
 * active purchase of charlie's L100, D2 bob's completed loan of diana's L103, and the one seeded
 * review is bob's review of charlie with no deal behind it.
 */
class ReviewServiceBranchTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final ReviewService reviews = new ReviewService(data);
    private final User alice = data.users().find("alice");
    private final User charlie = data.users().find("charlie");
    private final User diana = data.users().find("diana");

    @Test
    void aReviewNeedsSomeText() {
        Deal purchase = activeDeal("D1");
        data.deals().lifecycle().complete(purchase);

        assertEquals(ReviewService.TEXT_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.submitReview(alice, purchase, 4, null)).getMessage());
        assertEquals(ReviewService.TEXT_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.submitReview(alice, purchase, 4, "   ")).getMessage());
    }

    @Test
    void theListingOfAReviewIsFoundOnlyThroughAKnownDeal() {
        Review review = reviews.submitReview(data.users().find("bob"), activeDeal("D2"), 5, "Great loan");
        assertSame(data.listings().find("L103"), reviews.listingOf(review));
        assertNull(reviews.listingOf(data.reviews().all().get(0)), "the seeded review has no deal");

        Review orphan = new Review();
        orphan.setDealId("D999");
        assertNull(reviews.listingOf(orphan));
        assertNull(reviews.listingOf(new Review()));
        assertNull(reviews.listingOf(null));
    }

    @Test
    void aSellerCanFlagOnlyTheirOwnReviewsAndCanUnflag() {
        Review review = data.reviews().all().get(0);
        assertThrows(SecurityException.class, () -> reviews.flagReview(diana, review, true));

        reviews.flagReview(charlie, review, true);
        assertTrue(review.isFlagged());
        int adminNotices = data.notifications().forUser("admin", NotificationCategory.REPORTED_LISTING).size();

        reviews.flagReview(charlie, review, false);
        assertFalse(review.isFlagged());
        assertEquals(adminNotices, data.notifications().forUser("admin", NotificationCategory.REPORTED_LISTING).size(),
                "unflagging does not notify the admin");
    }

    @Test
    void aFailedFlagLeavesTheReviewUnflagged() {
        Review review = data.reviews().all().get(0);
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class, () -> reviews.flagReview(charlie, review, true));

        assertFalse(review.isFlagged());
    }

    @Test
    void aReportNeedsAReason() {
        Listing charger = data.listings().find("L101");
        assertEquals(ReviewService.REASON_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.reportListing(alice, charger, null)).getMessage());
    }

    private Deal activeDeal(String id) {
        return data.deals().allDeals().stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
