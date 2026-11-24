package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T2: one seller reply per review, persisted and notified to the buyer in one transaction. */
class SellerReviewReplyTest {

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
    void aNonEmptyReplyPersistsAndTellsTheBuyerExactlyOnce() {
        service.replyToReview(charlie, review, "  Thanks for the kind words!  ");

        assertEquals("Thanks for the kind words!", review.getSellerReply());
        assertEquals("Thanks for the kind words!", reloaded().getSellerReply());

        List<Notification> notices = data.notifications().forUser("bob", NotificationCategory.REVIEW_REPLY);
        assertEquals(1, notices.size());
        Notification notice = notices.get(0);
        assertEquals(new NotificationRef(NotificationRef.Target.REVIEW, review.getId()).toString(),
                notice.getRelatedRef().toString());
        assertTrue(notice.getMessage().contains("Thanks for the kind words!"));
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.REVIEW_REPLY).isEmpty(),
                "the seller must not be notified of their own reply");
    }

    @Test
    void aBlankReplyIsRefusedAndChangesNothing() {
        int noticesBefore = data.notifications().forUser("bob").size();

        assertEquals(SellerReviewService.REPLY_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> service.replyToReview(charlie, review, "   ")).getMessage());
        assertEquals(SellerReviewService.REPLY_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> service.replyToReview(charlie, review, null)).getMessage());

        assertNull(review.getSellerReply());
        assertNull(reloaded().getSellerReply());
        assertEquals(noticesBefore, data.notifications().forUser("bob").size());
    }

    @Test
    void aSecondReplyIsRefusedAndCannotOverwriteTheFirst() {
        service.replyToReview(charlie, review, "First reply");

        assertEquals(SellerReviewService.ALREADY_REPLIED, assertThrows(IllegalStateException.class,
                () -> service.replyToReview(charlie, review, "Second reply")).getMessage());

        assertEquals("First reply", reloaded().getSellerReply());
        assertEquals(1, data.notifications().forUser("bob", NotificationCategory.REVIEW_REPLY).size());
    }

    @Test
    void onlyTheOwningActiveSellerCanReply() {
        User diana = data.users().find("diana");
        assertThrows(SecurityException.class, () -> service.replyToReview(diana, review, "Not my review"));
        assertThrows(SecurityException.class,
                () -> service.replyToReview(data.users().find("bob"), review, "Buyers cannot reply"));
        charlie.setActive(false);
        assertThrows(SecurityException.class, () -> service.replyToReview(charlie, review, "Inactive"));

        assertNull(review.getSellerReply());
        assertNull(reloaded().getSellerReply());
        assertTrue(data.notifications().forUser("bob", NotificationCategory.REVIEW_REPLY).isEmpty());
    }

    @Test
    void aFailedNoticeRollsBackTheSavedReply() {
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class, () -> service.replyToReview(charlie, review, "Will not be saved"));

        assertNull(review.getSellerReply(), "the in-memory review must be restored after the rollback");
        assertNull(reloaded().getSellerReply());
        assertTrue(data.notifications().forUser("bob", NotificationCategory.REVIEW_REPLY).isEmpty());

        db.update("DROP TRIGGER fail_notice");
        service.replyToReview(charlie, review, "Retry after failure");
        assertEquals("Retry after failure", review.getSellerReply());
        assertEquals("Retry after failure", reloaded().getSellerReply());
    }

    private Review reloaded() {
        return DummyDataService.open(db).reviews().all().stream()
                .filter(r -> r.getId().equals(review.getId()))
                .findFirst().orElseThrow();
    }
}
