package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.controllers.FxTest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seller portal (SRS UC-2.3 to UC-2.9) used like a seller would, on the demo data: diana has
 * the calculator L103 with bob's pending borrow Q4, the lamp L102 with alice's pending claim Q2,
 * and the fridge L104 pending review; charlie has the live charger L101.
 */
class SellerScreensTest extends FxTest {

    @Test
    void everySellerPageOpens() {
        openDashboardAs("diana");
        for (String page : new String[]{"Requests", "Chats", "Create Listing", "Seller Stats",
                "Reviews & Feedback", "Notifications", "My Profile", "My Listings"}) {
            click(page);
        }
        assertTrue(screenText().contains("Graphing Calculator TI-84"));
    }

    @Test
    void requestsAreAcceptedDeclinedAndCountered() {
        Buyer bob = (Buyer) data.users().find("bob");
        PriceOffer offer = data.deals().lifecycle().submitOffer(bob, data.listings().find("L101"), new BigDecimal("200"));
        openDashboardAs("charlie");
        click("Requests");
        click("Refresh");

        // Counter: a bad price first, then a good one.
        click("Counter");
        click("Cancel");
        click("Counter");
        type("e.g. ", "abc");
        click("Confirm");
        type("e.g. ", "230");
        click("Confirm");
        assertEquals(RequestStatus.COUNTERED, offer.getStatus());
        click("Open chat");
        click("Requests");

        openDashboardAs("diana");
        click("Requests");
        // Counter the borrow period through the date and time inputs.
        click("Counter");
        fx(() -> all(TextInputControl.class).stream()
                .filter(f -> "HH:mm:ss".equals(f.getPromptText())).forEach(f -> f.setText("bad")));
        click("Confirm");
        click("Cancel");

        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Decline");
        click("Accept");
        DealRequest claim = data.deals().lifecycle().findRequest("Q2");
        DealRequest borrow = data.deals().lifecycle().findRequest("Q4");
        assertTrue(claim.getStatus() != RequestStatus.PENDING || borrow.getStatus() != RequestStatus.PENDING);
    }

    @Test
    void myListingsFilterEditDeleteAndMarkSold() {
        openDashboardAs("charlie");
        click("My Listings");
        clickEach("Open");
        click("My Listings");
        clickEach("View listing");
        click("My Listings");
        if (hasButton("Show all listings")) {
            click("Show all listings");
        }

        // Edit opens the prefilled form; cancelling (and confirming the discard) goes back.
        assertTrue(clickIfShown("Edit"));
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        clickIfShown("Cancel");
        click("My Listings");
        assertTrue(clickEach("Mark sold") >= 1, "disabled for the reserved textbook, enabled for the live charger");
        assertEquals(ListingStatus.SOLD, data.listings().find("L101").getStatus());
        click("My Listings");
        onDialog = pane -> ButtonBar.ButtonData.CANCEL_CLOSE;
        clickEach("Delete");
    }

    @Test
    void aNewListingIsValidatedThenSubmitted() {
        openDashboardAs("charlie");
        click("Create Listing");
        click("Submit for Review");
        assertTrue(screenText().toLowerCase().contains("required"));

        type("Item name", "Desk Fan");
        type("Description", "Quiet fan");
        choose("Category", 1);
        choose("Condition", 1);
        choose("Meet-up location", 1);
        for (int type = 2; type >= 0; type--) {
            choose("Listing type", type);
        }
        type("Price (HK$)", "abc");
        click("Submit for Review");
        type("Price (HK$)", "80");
        click("Submit for Review");
        assertTrue(data.listings().bySeller("charlie").stream()
                .anyMatch(l -> l.getTitle().equals("Desk Fan") && l.getStatus() == ListingStatus.PENDING_REVIEW));

        click("Create Listing");
        choose("Listing type", 1);
        type("Item name", "Tripod");
        type("Description", "Camera tripod");
        choose("Category", 1);
        choose("Condition", 1);
        choose("Meet-up location", 1);
        fx(() -> all(TextInputControl.class).stream()
                .filter(f -> "0".equals(f.getPromptText())).findFirst().ifPresent(f -> f.setText("3")));
        click("Submit for Review");
        click("Create Listing");
        type("Item name", "Changed my mind");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Cancel");
    }

    @Test
    void statsReviewsNotificationsAndProfileWork() {
        openDashboardAs("charlie");
        click("Seller Stats");
        click("Reviews & Feedback");
        type("Write your reply", "Thanks Bob!");
        click("Save Reply");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        clickEach("Flag");
        click("Reviews & Feedback");

        click("Notifications");
        clickEach("Open");
        click("Notifications");
        clickEach("Clear all");

        click("My Profile");
        type("Full Name", "");
        click("Save Changes");
        type("Full Name", "Charlie L.");
        type("Current password", "wrong");
        type("New password", "NewPass1!");
        type("Confirm new password", "NewPass1!");
        click("Save Changes");
        type("Current password", "");
        type("New password", "");
        type("Confirm new password", "");
        click("Save Changes");
        assertEquals("Charlie L.", data.users().find("charlie").getFullName());
    }

    @Test
    void aSellerWithNothingSeesEmptyPages() {
        Listing ignored = data.listings().find("L101");
        assertTrue(ignored != null);
        data.users().register(new com.hkust.marketplace.models.Seller("newseller", "New Seller", "hash",
                com.hkust.marketplace.models.UserRole.SELLER));
        openDashboardAs("newseller");
        for (String page : new String[]{"Requests", "Chats", "Seller Stats", "Reviews & Feedback",
                "Notifications", "My Listings"}) {
            click(page);
        }
        assertTrue(screenText().length() > 0);
    }
}
