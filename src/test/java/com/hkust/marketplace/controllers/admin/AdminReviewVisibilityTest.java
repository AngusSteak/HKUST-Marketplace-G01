package com.hkust.marketplace.controllers.admin;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hiding or unhiding a review asks for confirmation with state-specific wording (SRS UC-3.8 step 5.1 / A2). */
class AdminReviewVisibilityTest {

    @Test
    void hideAndUnhidePromptsDiffer() {
        assertNotEquals(AdminDashboardController.hideReviewMessage(false),
                AdminDashboardController.hideReviewMessage(true));
    }

    @Test
    void hidePromptMentionsHidingAndTheRating() {
        String message = AdminDashboardController.hideReviewMessage(false).toLowerCase(Locale.ROOT);
        assertTrue(message.contains("hide"), message);
        assertFalse(message.contains("unhide"), message);
        assertTrue(message.contains("rating"), message);
    }

    @Test
    void unhidePromptMentionsUnhidingAndTheRating() {
        String message = AdminDashboardController.hideReviewMessage(true).toLowerCase(Locale.ROOT);
        assertTrue(message.contains("unhide"), message);
        assertTrue(message.contains("rating"), message);
    }
}
