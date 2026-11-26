package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.ReportStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manage Reviews (SRS UC-1.9) and Report Listing (UC-1.10) from the buyer's side. */
class ReviewServiceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final ReviewService reviews = new ReviewService(data);
    private final User alice = data.users().find("alice");
    private final User bob = data.users().find("bob");

    @Test
    void finishedUnreviewedDealsAreReadyToReview() {
        Deal textbook = completeAlicesTextbook();

        assertEquals(List.of(textbook), reviews.reviewableDeals(alice));
        // bob's seeded loan was returned and completed, so it can be reviewed too
        assertEquals(1, reviews.reviewableDeals(bob).size());
    }

    @Test
    void aReviewIsSavedLinkedToTheDealUpdatesTheRatingAndTellsTheSeller() {
        Deal textbook = completeAlicesTextbook();

        Review review = reviews.submitReview(alice, textbook, 3, "  Fine, a bit late.  ");

        assertEquals("Fine, a bit late.", review.getComment());
        assertEquals(textbook.getId(), DummyDataService.open(db).reviews().forDeal(textbook.getId()).getDealId());
        assertEquals(4.0, data.reviews().averageRating("charlie").getAsDouble(), 0.001, "(5 + 3) / 2");
        Notification notice = data.notifications().forUser("charlie", NotificationCategory.REVIEW_RECEIVED).get(0);
        assertEquals("Alice Chan rated you 3/5 for 'Calculus Textbook (Stewart)'.", notice.getMessage());
        assertEquals("REVIEW:" + review.getId(), notice.getRelatedRef().toString());
        assertTrue(reviews.reviewableDeals(alice).isEmpty());
    }

    @Test
    void aDealIsReviewedAtMostOnce() {
        Deal textbook = completeAlicesTextbook();
        reviews.submitReview(alice, textbook, 5, "Great");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reviews.submitReview(alice, textbook, 4, "Again"));
        assertEquals(ReviewService.NOT_ELIGIBLE, e.getMessage());

        // a fresh service after a restart knows too, because the link is saved
        DummyDataService reopened = DummyDataService.open(db);
        Deal reloaded = reopened.deals().allDeals().stream()
                .filter(d -> d.getId().equals(textbook.getId())).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> new ReviewService(reopened)
                .submitReview(reopened.users().find("alice"), reloaded, 4, "Again"));
    }

    @Test
    void unfinishedDealsCannotBeReviewed() {
        Deal active = data.deals().activeDealsForBuyer("alice").get(0);

        assertEquals(ReviewService.NOT_ELIGIBLE,
                assertThrows(IllegalStateException.class, () -> reviews.submitReview(alice, active, 5, "Too soon"))
                        .getMessage());
    }

    @Test
    void missingRatingOrTextSaysWhichAndStoresNothing() {
        Deal textbook = completeAlicesTextbook();
        int before = data.reviews().all().size();

        assertEquals(ReviewService.RATING_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.submitReview(alice, textbook, 0, "Good")).getMessage());
        assertEquals(ReviewService.RATING_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.submitReview(alice, textbook, 6, "Good")).getMessage());
        assertEquals(ReviewService.TEXT_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.submitReview(alice, textbook, 4, "   ")).getMessage());
        assertEquals(before, data.reviews().all().size());
    }

    @Test
    void onlyTheDealsBuyerCanReviewIt() {
        Deal textbook = completeAlicesTextbook();
        assertThrows(SecurityException.class, () -> reviews.submitReview(bob, textbook, 1, "Never bought it"));
        assertThrows(SecurityException.class,
                () -> reviews.submitReview(data.users().find("charlie"), textbook, 5, "Me"));
    }

    @Test
    void myReviewsAreNewestFirstWithTheSellersReply() {
        Deal textbook = completeAlicesTextbook();
        Review review = reviews.submitReview(alice, textbook, 5, "Perfect");
        review.setSellerReply("Thanks Alice!");
        data.reviews().save(review);
        Deal loan = reviews.reviewableDeals(bob).get(0);
        reviews.submitReview(bob, loan, 4, "Worked well");

        List<Review> mine = new ReviewService(DummyDataService.open(db)).myReviews(alice);
        assertEquals(1, mine.size());
        assertEquals("Thanks Alice!", mine.get(0).getSellerReply());
        assertEquals(2, reviews.myReviews(bob).size(), "bob's seeded review and the new one");
        assertEquals("Worked well", reviews.myReviews(bob).get(0).getComment());
    }

    @Test
    void myReviewsOmitReviewsTheAdminHid() {
        Deal textbook = completeAlicesTextbook();
        Review visible = reviews.submitReview(alice, textbook, 5, "Perfect");
        visible.setSellerReply("Thanks Alice!");
        data.reviews().save(visible);
        Review hidden = reviews.submitReview(bob, reviews.reviewableDeals(bob).get(0), 2, "Never arrived");
        hidden.setHidden(true);
        data.reviews().save(hidden);

        List<Review> mine = reviews.myReviews(alice);
        assertEquals(1, mine.size());
        assertEquals(visible.getId(), mine.get(0).getId());
        assertEquals("Perfect", mine.get(0).getComment());
        assertEquals(5, mine.get(0).getRating());
        assertEquals("Thanks Alice!", mine.get(0).getSellerReply());

        List<Review> afterRestart = new ReviewService(DummyDataService.open(db)).myReviews(alice);
        assertEquals(1, afterRestart.size());
        assertEquals(visible.getId(), afterRestart.get(0).getId());

        List<Review> bobs = reviews.myReviews(bob);
        assertTrue(bobs.stream().noneMatch(r -> r.getId().equals(hidden.getId())), "the hidden review is gone");
        assertTrue(bobs.stream().anyMatch(r -> r.getComment().equals("Very smooth transaction, book in excellent condition!")));
    }

    @Test
    void theReviewedListingIsFoundThroughItsDeal() {
        Deal textbook = completeAlicesTextbook();
        Review review = reviews.submitReview(alice, textbook, 5, "Great");

        assertEquals("L100", reviews.listingOf(review).getId());
        assertNull(reviews.listingOf(data.reviews().all().get(0)), "a seeded review has no deal");
        assertNull(reviews.listingOf(null));
    }

    @Test
    void aReportIsStoredOpenAndEveryAdminIsTold() {
        Listing charger = data.listings().find("L101");

        ListingReport report = reviews.reportListing(bob, charger, "  Looks like a counterfeit  ");

        assertEquals(ReportStatus.OPEN, report.getStatus());
        assertEquals(1, DummyDataService.open(db).reports().openReports(charger).size());
        Notification notice = data.notifications().forUser("admin", NotificationCategory.REPORTED_LISTING).get(0);
        assertEquals("Bob Wong reported 'MacBook Pro Charger 61W': Looks like a counterfeit", notice.getMessage());
        assertEquals("LISTING:L101", notice.getRelatedRef().toString());
    }

    @Test
    void aReportNeedsAReason() {
        Listing charger = data.listings().find("L101");

        assertEquals(ReviewService.REASON_MISSING, assertThrows(IllegalArgumentException.class,
                () -> reviews.reportListing(bob, charger, "  ")).getMessage());
        assertTrue(data.reports().all().isEmpty());
        assertThrows(SecurityException.class, () -> reviews.reportListing(data.users().find("charlie"), charger, "x"));
    }

    @Test
    void aFailedReportLeavesNothingBehind() {
        Listing charger = data.listings().find("L101");
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class, () -> reviews.reportListing(bob, charger, "Spam"));

        assertTrue(data.reports().all().isEmpty());
        assertFalse(DummyDataService.open(db).reports().openReports(charger).size() > 0);
    }

    private Deal completeAlicesTextbook() {
        Deal deal = data.deals().activeDealsForBuyer("alice").stream()
                .filter(d -> d.getListing().getId().equals("L100")).findFirst().orElseThrow();
        new MyDealsService(data).markComplete(alice, deal);
        return deal;
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }

}
