package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T3: one seller flag per review, persisted, refused a second time, with no other side effects. */
class SellerReviewFlagTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerReviewService service;
    private User charlie;
    private Review review;

    @BeforeEach
    void setUp() {
        data = new DummyDataService(db);
        data.seedData();
        service = new SellerReviewService(data);
        charlie = data.users().find("charlie");
        review = data.reviews().forSeller("charlie").get(0);
    }

    @Test
    void flagPersistsAfterReload() {
        assertFalse(review.isFlagged());

        service.flagReview(charlie, review);

        assertTrue(review.isFlagged());
        assertTrue(reloaded().isFlagged());
    }

    @Test
    void aSecondFlagIsRefusedWithTheAlreadyFlaggedCondition() {
        service.flagReview(charlie, review);

        assertEquals(SellerReviewService.ALREADY_FLAGGED, assertThrows(IllegalStateException.class,
                () -> service.flagReview(charlie, review)).getMessage());
        assertTrue(reloaded().isFlagged());
    }

    @Test
    void aFlaggedReviewStaysVisibleAndKeepsItsRatingAndCount() {
        double averageBefore = data.reviews().averageRating("charlie").orElseThrow();
        int countBefore = data.reviews().visibleCount("charlie");

        service.flagReview(charlie, review);

        assertTrue(data.reviews().forSeller("charlie").stream()
                .anyMatch(r -> r.getId().equals(review.getId())));
        assertEquals(countBefore, data.reviews().visibleCount("charlie"));
        assertEquals(averageBefore, data.reviews().averageRating("charlie").orElseThrow(), 0.001);

        ReviewStore fresh = reloadedStore();
        assertEquals(countBefore, fresh.visibleCount("charlie"));
        assertEquals(averageBefore, fresh.averageRating("charlie").orElseThrow(), 0.001);
        assertEquals(1, fresh.forSeller("charlie").size());
    }

    @Test
    void onlyTheOwningActiveSellerCanFlag() {
        User diana = data.users().find("diana");
        assertThrows(SecurityException.class, () -> service.flagReview(diana, review));
        assertThrows(SecurityException.class, () -> service.flagReview(data.users().find("bob"), review));
        charlie.setActive(false);
        assertThrows(SecurityException.class, () -> service.flagReview(charlie, review));

        assertFalse(review.isFlagged());
        assertFalse(reloaded().isFlagged());
    }

    @Test
    void flaggingSendsNoNotificationToAnyone() {
        int noticesBefore = totalNotifications();

        service.flagReview(charlie, review);

        assertEquals(noticesBefore, totalNotifications());
    }

    private int totalNotifications() {
        Collection<User> users = data.users().all();
        int total = 0;
        for (User user : users) {
            for (Notification ignored : data.notifications().forUser(user.getUsername())) {
                total++;
            }
        }
        return total;
    }

    private Review reloaded() {
        return reloadedStore().all().stream()
                .filter(r -> r.getId().equals(review.getId()))
                .findFirst().orElseThrow();
    }

    private ReviewStore reloadedStore() {
        return DummyDataService.open(db).reviews();
    }
}
