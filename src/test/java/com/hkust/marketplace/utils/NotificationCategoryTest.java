package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationCategoryTest {

    @Test
    void appendixAHasExactlyFourteenCategoriesWithTheirLabels() {
        assertEquals(List.of(
                "Offer Update", "New Request", "Chat Message", "Borrow Reminder",
                "Listing Approved", "Listing Rejected", "Listing Removed", "Review Received",
                "Review Reply", "Deal Completed", "New Submission", "Reported Listing",
                "Account Change", "Announcement"),
                java.util.Arrays.stream(NotificationCategory.values())
                        .map(NotificationCategory::getLabel).toList());
        assertEquals(14, NotificationCategory.values().length);
    }

    @Test
    void migrationResolvesLabelsCaseInsensitively() {
        for (NotificationCategory category : NotificationCategory.values()) {
            assertSame(category, NotificationCategory.fromLabel(category.getLabel()));
        }
        assertSame(NotificationCategory.OFFER_UPDATE, NotificationCategory.fromLabel("offer update"));
        assertEquals("Offer Update", NotificationCategory.OFFER_UPDATE.toString());
        assertThrows(IllegalArgumentException.class, () -> NotificationCategory.fromLabel("Offer"));
    }

    @Test
    void notifySetsCategoryTitleAndRelatedRef() {
        NotificationStore store = new NotificationStore();
        NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, "L100");

        Notification n = store.notify("alice", NotificationCategory.LISTING_REMOVED, "Gone", ref);

        assertEquals(NotificationCategory.LISTING_REMOVED, n.getCategory());
        assertEquals("Listing Removed", n.getTitle());
        assertEquals("Gone", n.getMessage());
        assertSame(ref, n.getRelatedRef());
        assertEquals(NotificationRef.Target.LISTING, n.getRelatedRef().getTarget());
        assertEquals("L100", n.getRelatedRef().getId());
        assertEquals("N1", n.getId());
        assertFalse(n.isRead());
        assertNotNull(n.getTimestamp());
        assertEquals(List.of(n), store.forUser("alice"));
    }

    @Test
    void notifyAllowsNoRelatedRefForAnnouncements() {
        NotificationStore store = new NotificationStore();

        Notification n = store.notify("alice", NotificationCategory.ANNOUNCEMENT, "Welcome", null);

        assertEquals(NotificationCategory.ANNOUNCEMENT, n.getCategory());
        assertNull(n.getRelatedRef());
    }

    @Test
    void notifyRejectsBlankContent() {
        NotificationStore store = new NotificationStore();

        assertThrows(IllegalArgumentException.class,
                () -> store.notify("alice", NotificationCategory.ANNOUNCEMENT, "  ", null));
        assertThrows(NullPointerException.class,
                () -> store.notify("alice", null, "Content", null));
        assertTrue(store.forUser("alice").isEmpty());
    }

    @Test
    void relatedRefRequiresATargetAndAnId() {
        assertThrows(NullPointerException.class,
                () -> new NotificationRef(null, "L100"));
        assertThrows(IllegalArgumentException.class,
                () -> new NotificationRef(NotificationRef.Target.USER, " "));
        assertEquals("DEAL:D1",
                new NotificationRef(NotificationRef.Target.DEAL, "D1").toString());
    }

    @Test
    void seededNotificationsWereMigratedToCategoriesAndLinks() {
        NotificationStore store = new NotificationStore();
        store.seed();

        Notification alice = store.forUser("alice").get(0);
        assertEquals(NotificationCategory.OFFER_UPDATE, alice.getCategory());
        assertEquals("DEAL:D1", alice.getRelatedRef().toString());

        Notification charlie = store.forUser("charlie").get(0);
        assertEquals(NotificationCategory.OFFER_UPDATE, charlie.getCategory());
        assertEquals("REQUEST:Q1", charlie.getRelatedRef().toString());

        Notification diana = store.forUser("diana").get(0);
        assertEquals(NotificationCategory.NEW_SUBMISSION, diana.getCategory());
        assertEquals("LISTING:L104", diana.getRelatedRef().toString());

        Notification admin = store.forUser("admin").get(0);
        assertEquals(NotificationCategory.NEW_SUBMISSION, admin.getCategory());
        assertEquals("LISTING:L104", admin.getRelatedRef().toString());
    }
}
