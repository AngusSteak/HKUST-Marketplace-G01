package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyerNotificationTargetTest {

    @Test
    void dealTextIncludesTheKeyDetails() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 10, 30);
        LocalDateTime due = LocalDateTime.of(2026, 1, 9, 18, 0);

        String text = BuyerDashboardController.dealNotificationText("D100", DealType.LENT, DealStatus.ACTIVE,
                "Calculus Textbook", "Alice Wong", "Charlie Chan", start, due);

        assertTrue(text.contains("D100"), text);
        assertTrue(text.contains("Borrow"), text);
        assertTrue(text.contains("Active"), text);
        assertTrue(text.contains("Calculus Textbook"), text);
        assertTrue(text.contains("Alice Wong"), text);
        assertTrue(text.contains("Charlie Chan"), text);
        assertTrue(text.contains("2026-01-02 10:30"), text);
        assertTrue(text.contains("Due: 2026-01-09 18:00"), text);
    }

    @Test
    void finishedDealTextShowsTheCompletionDate() {
        LocalDateTime start = LocalDateTime.of(2026, 2, 1, 9, 0);
        LocalDateTime completed = LocalDateTime.of(2026, 2, 3, 16, 45);

        String text = BuyerDashboardController.dealNotificationText("D101", DealType.SOLD, DealStatus.COMPLETED,
                "Desk Lamp", "Alice Wong", "Charlie Chan", start, completed);

        assertTrue(text.contains("Buy"), text);
        assertTrue(text.contains("Completed"), text);
        assertTrue(text.contains("Completed: 2026-02-03 16:45"), text);
    }

    @Test
    void reviewTextIncludesRatingCommentBuyerDateAndReply() {
        LocalDateTime created = LocalDateTime.of(2026, 3, 4, 12, 15);

        String text = BuyerDashboardController.reviewNotificationText(4,
                "Smooth deal, item as described.", "Alice Wong", created, "Thanks, enjoy!");

        assertTrue(text.contains("4/5"), text);
        assertTrue(text.contains("\u2605\u2605\u2605\u2605\u2606"), text);
        assertTrue(text.contains("Smooth deal, item as described."), text);
        assertTrue(text.contains("Alice Wong"), text);
        assertTrue(text.contains("2026-03-04 12:15"), text);
        assertTrue(text.contains("Seller reply: Thanks, enjoy!"), text);
    }

    @Test
    void reviewTextOmitsTheReplyWhenThereIsNone() {
        String text = BuyerDashboardController.reviewNotificationText(5, "Great!", "Alice Wong",
                LocalDateTime.of(2026, 3, 4, 12, 15), null);

        assertFalse(text.contains("Seller reply"), text);
        assertTrue(text.contains("5/5"), text);
    }
}
