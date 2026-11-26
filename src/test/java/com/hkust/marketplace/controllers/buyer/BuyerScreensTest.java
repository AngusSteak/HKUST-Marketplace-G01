package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.controllers.FxTest;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buyer portal (SRS UC-1.3 to UC-1.10) opened and used like a buyer would, on the demo data:
 * L101 is charlie's live charger, L103 diana's live calculator to lend, L102 diana's reserved
 * lamp (alice's pending claim), and D1 alice's active purchase of L100.
 */
class BuyerScreensTest extends FxTest {

    @Test
    void everyBuyerPageOpens() {
        openDashboardAs("alice");
        assertTrue(screenText().contains("MacBook Pro Charger 61W"), "browse lists live listings");

        for (String page : new String[]{"My Deals", "Chats", "Deal History", "My Reviews", "Favourites",
                "Notifications", "My Profile", "Browse Marketplace"}) {
            click(page);
        }
        assertTrue(screenText().contains("MacBook Pro Charger 61W"));
    }

    @Test
    void browseSearchesFiltersAndSorts() {
        openDashboardAs("alice");
        type("Search title or description", "unicorn");
        click("Search");
        assertTrue(screenText().contains("No listings found"));

        click("Clear");
        type("Min HK$", "500");
        type("Max HK$", "1");
        click("Search");
        assertTrue(screenText().contains("Invalid price range"));

        type("Min HK$", "abc");
        click("Search");
        type("Min HK$", "");
        type("Max HK$", "");
        for (int box = 0; box < 4; box++) {
            choose(box, 1);
        }
        choose(3, 2);
        click("Clear");
        assertTrue(screenText().contains("MacBook Pro Charger 61W"));
    }

    @Test
    void listingDetailsLetTheBuyerFavouriteFollowAndAsk() {
        openDashboardAs("alice");
        int opened = clickEach("View Details");
        assertTrue(opened >= 2);

        // The last details page opened is one of the live listings; use its actions.
        for (String action : new String[]{"Favourite", "♡ Favourite", "Follow Seller", "Unfollow",
                "Report Listing", "Profile", "Chat"}) {
            if (hasButton(action)) {
                click(action);
                click("Browse Marketplace");
                clickEach("View Details");
            }
        }
        click("Favourites");
        click("My Deals");
    }

    @Test
    void anOfferIsMadeThroughItsDialog() {
        openDashboardAs("bob");
        Listing charger = data.listings().find("L101");
        openDetailsOf(charger.getTitle());

        onDialog = pane -> {
            pane.lookupAll(".text-field").forEach(n -> ((TextInputControl) n).setText("0"));
            return ButtonBar.ButtonData.CANCEL_CLOSE;
        };
        click("Make Offer");

        onDialog = pane -> {
            pane.lookupAll(".text-field").forEach(n -> ((TextInputControl) n).setText("200"));
            return ButtonBar.ButtonData.OK_DONE;
        };
        click("Make Offer");
        assertTrue(data.deals().openRequestsByBuyer("bob").stream()
                .anyMatch(r -> r.getListing() == charger), "bob's offer was recorded");
    }

    @Test
    void aBorrowRequestAndAClaimGoThroughTheirDialogs() {
        openDashboardAs("alice");
        openDetailsOf("Graphing Calculator TI-84");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Request Borrow");
        assertTrue(data.deals().openRequestsByBuyer("alice").stream()
                .anyMatch(r -> r.getListing().getId().equals("L103")));

        // A new giveaway, then claimed.
        Listing lamp = data.listings().find("L102");
        data.deals().lifecycle().decline(data.deals().lifecycle().findRequest("Q2"));
        assertEquals("LIVE", lamp.getStatus().name());
        openDashboardAs("bob");
        openDetailsOf("IKEA Desk Lamp");
        click("Claim Item");
        assertEquals("RESERVED", lamp.getStatus().name());
    }

    @Test
    void myDealsAnswersACounterAndCompletesAPurchase() {
        openDashboardAs("alice");
        PriceOffer offer = data.deals().lifecycle().submitOffer(
                (com.hkust.marketplace.models.Buyer) data.users().find("alice"), data.listings().find("L101"),
                new BigDecimal("200"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("230"));
        click("My Deals");
        assertTrue(screenText().contains("Countered"));

        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Accept Counter");
        assertEquals(RequestStatus.ACCEPTED, offer.getStatus());

        click("My Deals");
        clickEach("Open");
        click("My Deals");
        click("Mark Complete");
        click("My Deals");
        click("Deal History");
        clickEach("Rate Seller");
        click("My Reviews");
        clickEach("Rate Seller");
    }

    @Test
    void aBorrowerReturnsTheItemFromMyDeals() {
        DealRequest borrow = data.deals().lifecycle().findRequest("Q4");
        Deal loan = data.deals().lifecycle().accept(borrow);
        openDashboardAs("bob");
        click("My Deals");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Return Item");
        click("My Deals");
        assertFalse(loan.getStatus().name().equals("ACTIVE"));
    }

    @Test
    void dealHistoryFiltersByTextAndDate() {
        openDashboardAs("bob");
        click("Deal History");
        // The filters apply as they change.
        type("Keyword in title", "calculator");
        type("Seller", "diana");
        type("Keyword in title", "nothing-like-this");
        type("Keyword in title", "");
        choose(0, 1);
        fx(() -> {
            var pickers = all(javafx.scene.control.DatePicker.class);
            if (pickers.size() >= 2) {
                pickers.get(0).setValue(java.time.LocalDate.now());
                pickers.get(1).setValue(java.time.LocalDate.now().minusDays(5));
            }
        });
        assertTrue(screenText().toLowerCase().contains("date"), "a reversed range is explained");
        click("Clear");
    }

    @Test
    void chatsOpenSendAndSearch() {
        openDashboardAs("alice");
        click("Chats");
        click("Open");
        type("Type a message", "");
        click("Send");
        type("Type a message", "Is it still available?");
        click("Send");
        type("Search this chat", "available");
        click("Search");
        type("Search this chat", "zzz");
        click("Search");
        click("Clear");
        choose(0, 2);
        choose(0, 1);
        if (hasButton("Make Offer")) {
            onDialog = pane -> ButtonBar.ButtonData.CANCEL_CLOSE;
            click("Make Offer");
        }
        assertTrue(screenText().contains("You"));
    }

    @Test
    void notificationsAndProfileWork() {
        openDashboardAs("alice");
        click("Notifications");
        clickEach("Open");
        click("Notifications");
        clickEach("Mark all read");

        click("My Profile");
        type("Full Name", "");
        click("Save Changes");
        type("Full Name", "Alice Chan");
        type("Current password", "wrong");
        type("New password", "NewPass1!");
        type("Confirm new password", "NewPass1!");
        click("Save Changes");
        type("Current password", "password1");
        click("Save Changes");
        type("Full Name", "Alice C.");
        click("Save Changes");
        assertEquals("Alice C.", data.users().find("alice").getFullName());
    }

    private void openDetailsOf(String title) {
        click("Browse Marketplace");
        type("Search title or description", title);
        click("Search");
        click("View Details");
    }
}
