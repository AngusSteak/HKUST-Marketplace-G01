package com.hkust.marketplace.controllers.seller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SellerRequestChatNavigationTest {

    @Test
    void chatsTabKeepsItsOwnBackLabel() {
        assertEquals("\u2190 Back to Chats", SellerDashboardController.threadBackLabel(false));
    }

    @Test
    void chatOpenedFromRequestsUsesBackToRequestsLabel() {
        assertEquals("\u2190 Back to Requests", SellerDashboardController.threadBackLabel(true));
    }

    @Test
    void requestsBackLabelIsBackToChat() {
        assertEquals("\u2190 Back to Chat", SellerRequestsController.backToChatLabel());
    }
}
