package com.hkust.marketplace.controllers.admin;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deactivating or reactivating an account asks for confirmation with state-specific wording (SRS UC-3.3 step 3.3.1). */
class AdminAccountStatusTest {

    @Test
    void deactivateAndReactivatePromptsDiffer() {
        assertNotEquals(AdminDashboardController.accountStatusMessage(true),
                AdminDashboardController.accountStatusMessage(false));
    }

    @Test
    void deactivatePromptMentionsDeactivationAndBlockedLogin() {
        String message = AdminDashboardController.accountStatusMessage(true).toLowerCase(Locale.ROOT);
        assertTrue(message.contains("deactivate"), message);
        assertFalse(message.contains("reactivate this account"), message);
        assertTrue(message.contains("blocked"), message);
    }

    @Test
    void reactivatePromptMentionsReactivationAndAllowedLogin() {
        String message = AdminDashboardController.accountStatusMessage(false).toLowerCase(Locale.ROOT);
        assertTrue(message.contains("reactivate"), message);
        assertTrue(message.contains("allowed"), message);
        assertTrue(message.contains("log in"), message);
    }
}
