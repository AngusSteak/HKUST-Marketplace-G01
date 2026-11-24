package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.NotificationCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hkust.marketplace.models.NotificationCategory.ANNOUNCEMENT;
import static com.hkust.marketplace.models.NotificationCategory.CHAT_MESSAGE;
import static com.hkust.marketplace.models.NotificationCategory.DEAL_COMPLETED;
import static com.hkust.marketplace.models.NotificationCategory.LISTING_APPROVED;
import static com.hkust.marketplace.models.NotificationCategory.LISTING_REJECTED;
import static com.hkust.marketplace.models.NotificationCategory.NEW_REQUEST;
import static com.hkust.marketplace.models.NotificationCategory.REVIEW_RECEIVED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SellerNotificationBoardTest {

    @Test
    void sellerCategoriesAreExactlyTheSevenSrsCategoriesInOrder() {
        assertEquals(List.of(LISTING_APPROVED, LISTING_REJECTED, NEW_REQUEST, REVIEW_RECEIVED,
                DEAL_COMPLETED, CHAT_MESSAGE, ANNOUNCEMENT),
                SellerDashboardController.sellerCategories());
        assertEquals(7, SellerDashboardController.sellerCategories().size());
    }

    @Test
    void sellerCategoriesExcludeRemovedAndBuyerOnlyCategories() {
        List<NotificationCategory> categories = SellerDashboardController.sellerCategories();
        assertFalse(categories.contains(NotificationCategory.LISTING_REMOVED));
        assertFalse(categories.contains(NotificationCategory.OFFER_UPDATE));
        assertFalse(categories.contains(NotificationCategory.BORROW_REMINDER));
        assertFalse(categories.contains(NotificationCategory.REVIEW_REPLY));
    }

    @Test
    void parseThreadRefSplitsBuyerAndListingAtTheFirstBar() {
        assertArrayEquals(new String[]{"bob", "L101"}, SellerDashboardController.parseThreadRef("bob|L101"));
    }

    @Test
    void parseThreadRefTrimsBothHalves() {
        assertArrayEquals(new String[]{"bob", "L101"}, SellerDashboardController.parseThreadRef("  bob | L101 "));
    }

    @Test
    void parseThreadRefRejectsMalformedAndNullIds() {
        assertNull(SellerDashboardController.parseThreadRef("bob"));
        assertNull(SellerDashboardController.parseThreadRef("|L101"));
        assertNull(SellerDashboardController.parseThreadRef("bob|"));
        assertNull(SellerDashboardController.parseThreadRef(null));
    }
}
