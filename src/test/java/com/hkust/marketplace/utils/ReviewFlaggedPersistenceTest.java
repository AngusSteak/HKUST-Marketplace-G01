package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A review's flag survives a reopen from the same database, like reply and hidden. */
class ReviewFlaggedPersistenceTest {

    private final Database db = Database.inMemory();
    private ReviewStore store;

    @BeforeEach
    void setUp() {
        UserStore users = new UserStore(db);
        users.register(new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER));
        users.register(new Buyer("bob", "Bob Wong", "hash", UserRole.BUYER));
        store = new ReviewStore(db);
    }

    @Test
    void flaggedTrueSurvivesAddAndReloadWithReplyAndRatingIntact() {
        Review review = review(4, "Thanks for shopping", true);
        store.add(review);

        Review back = reloaded(review);

        assertTrue(back.isFlagged());
        assertEquals("Thanks for shopping", back.getSellerReply());
        assertEquals(4, back.getRating());
    }

    @Test
    void flaggedFalseSurvivesAddAndReloadWithReplyAndRatingIntact() {
        Review review = review(2, "No reply yet", false);
        store.add(review);

        Review back = reloaded(review);

        assertFalse(back.isFlagged());
        assertEquals("No reply yet", back.getSellerReply());
        assertEquals(2, back.getRating());
    }

    @Test
    void savePersistsAChangedFlagAlongsideTheReply() {
        Review review = review(3, "Original reply", false);
        store.add(review);

        review.setFlagged(true);
        review.setSellerReply("Updated reply");
        store.save(review);

        Review back = reloaded(review);
        assertTrue(back.isFlagged());
        assertEquals("Updated reply", back.getSellerReply());
        assertEquals(3, back.getRating());
    }

    @Test
    void aFlaggedReviewStaysVisibleAndStillCountsTowardsTheRating() {
        store.add(review(5, "Fine", true));

        assertEquals(1, store.forSeller("charlie").size());
        assertEquals(1, store.visibleCount("charlie"));
        assertEquals(5.0, store.averageRating("charlie").getAsDouble(), 0.001);
    }

    private Review review(int rating, String sellerReply, boolean flagged) {
        Review review = new Review();
        review.setSellerUsername("charlie");
        review.setBuyerUsername("bob");
        review.setListingTitle("Old Chemistry Book");
        review.setRating(rating);
        review.setComment("A comment that must not change");
        review.setSellerReply(sellerReply);
        review.setFlagged(flagged);
        return review;
    }

    private Review reloaded(Review review) {
        ReviewStore fresh = new ReviewStore(db);
        fresh.load();
        return fresh.all().stream()
                .filter(r -> r.getId().equals(review.getId()))
                .findFirst()
                .orElseThrow();
    }
}
