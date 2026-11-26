package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.controllers.FxTest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ListingStatus;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin portal (SRS UC-3.2 to UC-3.8) used like an admin would, on the demo data: diana's
 * fridge L104 is pending review, charlie's charger L101 is live, and bob's review of charlie is
 * the one rating.
 */
class AdminScreensTest extends FxTest {

    @Test
    void everyAdminPageOpens() {
        openDashboardAs("admin");
        assertTrue(screenText().contains("Mini Fridge"), "the pending listing is waiting for review");
        for (String page : new String[]{"All Listings", "Manage Users", "All Deals", "Seller Ratings",
                "Notifications", "My Profile", "Listing Review"}) {
            click(page);
        }
    }

    @Test
    void aPendingListingIsApproved() {
        openDashboardAs("admin");
        clickIfShown("View");
        click("Listing Review");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Approve");
        assertEquals(ListingStatus.LIVE, data.listings().find("L104").getStatus());
    }

    @Test
    void aPendingListingIsRejectedWithAReason() {
        openDashboardAs("admin");
        onDialog = pane -> {
            pane.lookupAll(".text-input").forEach(n -> ((TextInputControl) n).setText(""));
            return ButtonBar.ButtonData.OK_DONE;
        };
        click("Reject");
        onDialog = pane -> {
            pane.lookupAll(".text-input").forEach(n -> ((TextInputControl) n).setText("Photos missing"));
            return ButtonBar.ButtonData.OK_DONE;
        };
        click("Reject");
        assertEquals(ListingStatus.REJECTED, data.listings().find("L104").getStatus());
    }

    @Test
    void allListingsAreSearchedEditedReportedAndTakenDown() {
        new com.hkust.marketplace.utils.ReviewService(data).reportListing(
                data.users().find("alice"), data.listings().find("L101"), "Looks fake");
        openDashboardAs("admin");
        click("All Listings");
        type("Search title or seller", "charger");
        type("Search title or seller", "");
        tryEveryChoice();

        // Reports on the charger are dismissed together with one note.
        onDialog = pane -> {
            pane.lookupAll(".text-input").forEach(n -> ((TextInputControl) n).setText("Checked, it is fine"));
            // The reports dialog offers "Dismiss all with note"; the note dialog after it has OK.
            boolean reportsDialog = pane.getButtonTypes().stream()
                    .anyMatch(t -> t.getButtonData() == ButtonBar.ButtonData.LEFT);
            return reportsDialog ? ButtonBar.ButtonData.LEFT : ButtonBar.ButtonData.OK_DONE;
        };
        clickEach("Reports");   // listings without reports just say so
        click("All Listings");
        click("Reports (1)");
        assertTrue(data.reports().openReports(data.listings().find("L101")).isEmpty());
        click("All Listings");

        // Edit: a blank title keeps the dialog open with an error, so the admin cancels;
        // a second edit is saved.
        int[] attempts = {0};
        onDialog = pane -> {
            if (attempts[0]++ == 0) {
                pane.lookupAll(".text-field").stream().findFirst()
                        .ifPresent(n -> ((TextInputControl) n).setText(""));
                return ButtonBar.ButtonData.OK_DONE;
            }
            return ButtonBar.ButtonData.CANCEL_CLOSE;
        };
        click("Edit");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("All Listings");
        click("Edit");
        click("All Listings");

        onDialog = pane -> {
            pane.lookupAll(".text-input").forEach(n -> ((TextInputControl) n).setText("Breaks the rules"));
            return ButtonBar.ButtonData.OK_DONE;
        };
        clickEach("Take down");
        assertTrue(data.listings().all().stream().anyMatch(l -> l.getStatus() == ListingStatus.TAKEN_DOWN));
    }

    @Test
    void aListingIsAddedOnBehalfOfASeller() {
        openDashboardAs("admin");
        click("All Listings");
        click("+ Add listing on behalf of a seller");
        click("Save as Live");
        choose("Seller who owns this listing", 0);
        type("Item name", "Desk Fan");
        type("Description", "Quiet fan");
        choose("Category", 1);
        choose("Condition", 1);
        choose("Meet-up location", 1);
        choose("Listing type", 2);
        click("Save as Live");
        assertTrue(data.listings().bySeller("charlie").stream()
                .anyMatch(l -> l.getTitle().equals("Desk Fan") && l.getStatus() == ListingStatus.LIVE));
    }

    @Test
    void usersAreSearchedAddedEditedDeactivatedAndDeleted() {
        openDashboardAs("admin");
        click("Manage Users");
        type("Search username or name", "bob");
        type("Search username or name", "");

        // Add a seller through the dialog: mismatched passwords first, then a valid account.
        String[][] attempts = {{"erin", "Erin Ma", "Password1!", "Different1!"},
                {"erin", "Erin Ma", "Password1!", "Password1!"}};
        for (String[] values : attempts) {
            onDialog = pane -> {
                var combos = new java.util.ArrayList<>(pane.lookupAll(".combo-box"));
                if (!combos.isEmpty()) {
                    ((javafx.scene.control.ComboBox<?>) combos.get(0)).getSelectionModel().select(1);
                }
                var fields = new java.util.ArrayList<>(pane.lookupAll(".text-field"));
                for (int i = 0; i < Math.min(values.length, fields.size()); i++) {
                    ((TextInputControl) fields.get(i)).setText(values[i]);
                }
                return ButtonBar.ButtonData.OK_DONE;
            };
            click("+ Add user");
        }
        assertTrue(data.users().find("erin") != null, "the account was created");

        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        clickEach("Edit");
        click("Manage Users");
        assertTrue(clickEach("Deactivate") >= 1);
        click("Manage Users");
        clickEach("Reactivate");
        click("Manage Users");
        Buyer bob = (Buyer) data.users().find("bob");
        type("Search username or name", "bob");
        click("Delete");
        assertTrue(bob.isDeleted());
        click("Manage Users");
    }

    @Test
    void dealsAreFilteredByTextAndDate() {
        openDashboardAs("admin");
        click("All Deals");
        type("Search title, buyer or seller", "calculus");
        type("Search title, buyer or seller", "");
        tryEveryChoice();
        fx(() -> {
            var pickers = all(DatePicker.class);
            pickers.get(0).setValue(LocalDate.now().plusDays(10));
            pickers.get(1).setValue(LocalDate.now());
        });
        fx(() -> {
            var pickers = all(DatePicker.class);
            pickers.get(0).setValue(LocalDate.now().minusYears(1));
            pickers.get(1).setValue(LocalDate.now().plusDays(1));
        });
        clickEach("View");
        click("All Deals");
    }

    @Test
    void ratingsAreFilteredHiddenAndShownAgain() {
        openDashboardAs("admin");
        click("Seller Ratings");
        type("Filter by seller", "charlie");
        type("Filter by seller", "");
        onDialog = pane -> ButtonBar.ButtonData.OK_DONE;
        click("Hide");
        assertTrue(data.reviews().all().get(0).isHidden());
        click("Unhide");
        assertFalse(data.reviews().all().get(0).isHidden());
        fx(() -> {
            var pickers = all(DatePicker.class);
            if (pickers.size() >= 2) {
                pickers.get(0).setValue(LocalDate.now().plusDays(3));
                pickers.get(1).setValue(LocalDate.now());
            }
        });
    }

    @Test
    void notificationsAndProfileWork() {
        openDashboardAs("admin");
        click("Notifications");
        clickEach("Open");
        click("My Profile");
        type("Current password", "wrong");
        type("New password", "NewPass1!");
        type("Confirm new password", "NewPass1!");
        click("Save");
        type("Current password", "admin123");
        click("Save");
        click("Logout");
    }
}
