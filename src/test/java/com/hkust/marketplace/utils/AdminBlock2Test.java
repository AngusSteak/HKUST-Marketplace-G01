package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Block-2 admin actions (#32–#37) save, audit, notify and keep their invariants. */
class AdminBlock2Test {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final AdminService admin = new AdminService(data);
    private final User adminUser = data.users().find("admin");

    @Test
    void addedAccountsAreValidatedAndSurviveAReload() {
        User buyer = admin.addAccount(adminUser, UserRole.BUYER, "Frank", "Frank Ho",
                "Password1!", "Hall 3", null, null);
        Seller seller = (Seller) admin.addAccount(adminUser, UserRole.SELLER, "grace", "Grace Lam",
                "Password1!", null, "Sells plants", Location.LIBRARY);

        assertEquals(UserRole.BUYER, buyer.getRole());
        assertEquals("Hall 3", ((Buyer) buyer).getResidenceHall());
        assertEquals("Sells plants", seller.getBio());
        assertEquals(Location.LIBRARY, seller.getPreferredMeetup().iterator().next());
        assertEquals(LoginGate.Outcome.OK, LoginGate.check(buyer, UserRole.BUYER, "Password1!"));

        assertThrows(IllegalArgumentException.class, () -> admin.addAccount(adminUser, UserRole.BUYER,
                "FRANK", "Impostor", "Password1!", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> admin.addAccount(adminUser, UserRole.BUYER,
                "weak", "Weak", "password", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> admin.addAccount(adminUser, UserRole.ADMIN,
                "root", "Root", "Password1!", null, null, null));

        DummyDataService saved = DummyDataService.open(db);
        assertNotNull(saved.users().find("frank"));
        assertNotNull(saved.users().find("grace"));
        assertEquals(List.of("Added account", "Added account"),
                saved.audit().all().stream().map(e -> e.getAction()).toList());
    }

    @Test
    void editingAnAccountChangesOnlyItsEditableFields() {
        User alice = data.users().find("alice");
        admin.updateAccount(adminUser, alice, "Alice C.", "Hall 1", null, null);

        DummyDataService saved = DummyDataService.open(db);
        User back = saved.users().find("alice");
        assertEquals("Alice C.", back.getFullName());
        assertEquals("Hall 1", ((Buyer) back).getResidenceHall());
        assertEquals(UserRole.BUYER, back.getRole());
        assertTrue(back.isActive());
        assertFalse(back.isDeleted());
        assertEquals(NotificationCategory.ANNOUNCEMENT,
                saved.notifications().forUser("alice").get(0).getCategory());

        assertThrows(IllegalArgumentException.class,
                () -> admin.updateAccount(adminUser, alice, "   ", null, null, null));
        assertThrows(IllegalStateException.class,
                () -> admin.updateAccount(adminUser, adminUser, "Root", null, null, null));
    }

    @Test
    void deactivationBlocksLoginHidesListingsAndNotifies() {
        User charlie = data.users().find("charlie");
        admin.setActive(adminUser, charlie, false);

        DummyDataService saved = DummyDataService.open(db);
        User back = saved.users().find("charlie");
        assertFalse(back.isActive());
        assertEquals(LoginGate.Outcome.DISABLED, LoginGate.check(back, UserRole.SELLER, "password1"));
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus(),
                "the listing keeps its status; the account state hides it");
        assertFalse(ListingSearch.isBrowsable(saved.listings().find("L101")));
        assertEquals(NotificationCategory.ANNOUNCEMENT,
                saved.notifications().forUser("charlie").get(0).getCategory());
        assertEquals("Deactivated account", saved.audit().all().get(0).getAction());

        admin.setActive(adminUser, data.users().find("charlie"), true);
        assertTrue(DummyDataService.open(db).users().find("charlie").isActive());
    }

    @Test
    void softDeleteCascadesToTheSellersListingsDealsAndHistory() {
        User charlie = data.users().find("charlie");
        assertEquals(2, admin.accountActivity(adminUser, charlie).listings().size());

        admin.softDeleteAccount(adminUser, charlie);

        DummyDataService saved = DummyDataService.open(db);
        User back = saved.users().find("charlie");
        assertTrue(back.isDeleted());
        assertFalse(back.isActive());
        assertEquals("Deleted user", back.getFullName());
        assertNull(((Seller) back).getBio());
        assertEquals(LoginGate.Outcome.DELETED, LoginGate.check(back, UserRole.SELLER, "password1"));
        for (String id : List.of("L100", "L101")) {
            assertEquals(ListingStatus.TAKEN_DOWN, saved.listings().find(id).getStatus());
            assertEquals(AdminService.ACCOUNT_DELETED_REASON, saved.listings().find(id).getTakedownReason());
        }
        assertTrue(saved.deals().activeDealsForBuyer("alice").isEmpty());
        Deal cancelled = saved.deals().allDeals().stream()
                .filter(d -> d.getStatus() == DealStatus.CANCELLED).findFirst().orElseThrow();
        assertEquals("L100", cancelled.getListing().getId());
        assertTrue(saved.notifications().forUser("alice").stream()
                .anyMatch(n -> n.getCategory() == NotificationCategory.OFFER_UPDATE
                        && n.getMessage().contains("was deleted")), "the buyer hears it as an Offer Update");
        assertEquals("Deleted account", saved.audit().all().get(0).getAction());
        assertEquals("@charlie", saved.audit().all().get(0).getTarget());
    }

    @Test
    void softDeleteAsBuyerDeclinesRequestsAndLeavesTheListingUp() {
        User bob = data.users().find("bob");
        admin.softDeleteAccount(adminUser, bob);

        DummyDataService saved = DummyDataService.open(db);
        assertTrue(saved.deals().openRequestsByBuyer("bob").isEmpty());
        assertTrue(saved.deals().requestsByBuyer("bob").stream()
                .allMatch(r -> r.getStatus() == com.hkust.marketplace.models.RequestStatus.DECLINED
                        || r.getStatus() == com.hkust.marketplace.models.RequestStatus.ACCEPTED));
        assertEquals(ListingStatus.LIVE, saved.listings().find("L103").getStatus());
        assertTrue(saved.notifications().forUser("diana").stream()
                .anyMatch(n -> n.getCategory() == NotificationCategory.ANNOUNCEMENT
                        && n.getMessage().contains("was deleted")), "the seller hears it as an Announcement");
        assertTrue(saved.users().find("bob").isDeleted());
    }

    @Test
    void adminCanEditAListingWithoutChangingItsStatus() {
        Listing charger = data.listings().find("L101");
        admin.editListing(adminUser, charger, "MacBook charger", "New description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "299", null);

        DummyDataService saved = DummyDataService.open(db);
        Listing back = saved.listings().find("L101");
        assertEquals("MacBook charger", back.getTitle());
        assertEquals(ListingStatus.LIVE, back.getStatus());
        assertEquals(299.0, back instanceof com.hkust.marketplace.models.SaleListing sale
                ? sale.getPrice().amount().doubleValue() : -1);
        assertEquals("Edited", saved.audit().all().get(0).getAction());
        assertEquals(NotificationCategory.ANNOUNCEMENT,
                saved.notifications().forUser("charlie").get(0).getCategory());

        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, back,
                "Still Live", "", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "not a price", null));
        assertEquals("MacBook charger", data.listings().find("L101").getTitle());
    }

    @Test
    void adminCanAddAListingOnBehalfAndItStartsLive() {
        Seller diana = (Seller) data.users().find("diana");
        ListingFormState state = new ListingFormState();
        state.setType(ListingType.SELL);
        state.setTitle("Desk chair");
        state.setDescription("Ergonomic chair");
        state.setCategory(Category.FURNITURE);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.UG_HALL_V);
        state.setRawPrice("150");
        PhotoStorage photos = (source, username, listingId) -> "unused";

        Listing added = admin.addListingOnBehalf(adminUser, diana, state, photos);

        assertEquals(ListingStatus.LIVE, added.getStatus());
        assertEquals(diana.getUsername(), added.getSellerUsername());
        assertNotNull(added.getReviewedAt());
        DummyDataService saved = DummyDataService.open(db);
        assertEquals("Desk chair", saved.listings().find(added.getId()).getTitle());
        assertEquals("Added listing on behalf", saved.audit().all().get(0).getAction());
        assertTrue(saved.notifications().forUser("diana").stream()
                .anyMatch(n -> n.getMessage().contains("Desk chair")));
    }

    @Test
    void addOnBehalfRefusesAnInactiveSeller() {
        Seller charlie = (Seller) data.users().find("charlie");
        admin.setActive(adminUser, charlie, false);
        ListingFormState state = new ListingFormState();
        state.setType(ListingType.GIVEAWAY);
        state.setTitle("Old notes");
        state.setDescription("Free");
        state.setCategory(Category.BOOKS_AND_NOTES);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.LIBRARY);
        PhotoStorage photos = (source, username, listingId) -> "unused";

        assertThrows(IllegalStateException.class,
                () -> admin.addListingOnBehalf(adminUser, charlie, state, photos));
    }

    @Test
    void allOpenReportsOnAListingAreDismissedWithOneNote() {
        Buyer alice = (Buyer) data.users().find("alice");
        Buyer bob = (Buyer) data.users().find("bob");
        Listing charger = data.listings().find("L101");
        data.reports().fileReport(alice, charger, "Looks fake");
        data.reports().fileReport(bob, charger, "Wrong price");
        ListingReport other = data.reports().fileReport(bob, data.listings().find("L103"), "Spam");

        assertEquals(AdminService.RESOLUTION_NOTE_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> admin.dismissReports(adminUser, charger, "  ")).getMessage());
        assertEquals(2, admin.dismissReports(adminUser, charger, "  Checked with the seller, it is genuine "));

        DummyDataService saved = DummyDataService.open(db);
        assertTrue(saved.reports().openReports(saved.listings().find("L101")).isEmpty());
        assertTrue(saved.reports().all().stream().filter(r -> r.getListing().getId().equals("L101"))
                .allMatch(r -> "Checked with the seller, it is genuine".equals(r.getResolution())));
        assertEquals(1, saved.reports().openReports(saved.listings().find("L103")).size(), "other listings untouched");
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus(), "dismissing keeps the listing");
        assertEquals("Resolved report", saved.audit().all().get(0).getAction());
        assertEquals("L101", saved.audit().all().get(0).getTarget());
        assertEquals(1, saved.audit().all().size(), "one audit entry for the whole dismissal");

        assertThrows(IllegalStateException.class, () -> admin.dismissReports(adminUser, charger, "Again"));
        assertThrows(SecurityException.class,
                () -> admin.dismissReports(data.users().find("alice"), other.getListing(), "Not mine"));
    }

    @Test
    void adminEditsNeedADescriptionLikePostListing() {
        Listing charger = data.listings().find("L101");
        String before = charger.getDescription();

        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, charger, "Charger", "   ",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "250", null));

        assertEquals(before, DummyDataService.open(db).listings().find("L101").getDescription());
        assertTrue(data.audit().all().isEmpty());
    }

    @Test
    void flaggedReviewsPersistAndHiddenOnesRecalculateTheRating() {
        Review review = data.reviews().all().get(0);
        new ReviewService(data).flagReview(data.users().find("charlie"), review, true);

        DummyDataService flagged = DummyDataService.open(db);
        assertTrue(flagged.reviews().all().get(0).isFlagged());
        assertTrue(flagged.notifications().forUser("admin").stream()
                .anyMatch(n -> n.getCategory() == NotificationCategory.REPORTED_LISTING));

        admin.setReviewHidden(adminUser, review, true);
        DummyDataService hidden = DummyDataService.open(db);
        assertTrue(hidden.reviews().all().get(0).isHidden());
        assertTrue(hidden.reviews().all().get(0).isFlagged());
        assertTrue(hidden.reviews().averageRating("charlie").isEmpty());
    }

    @Test
    void adminProfileRequiresANameAndAllThreePasswordFields() {
        admin.updateProfile(adminUser, "  New Admin  ", "admin123", "NewPass1!", "NewPass1!");

        DummyDataService saved = DummyDataService.open(db);
        User back = saved.users().find("admin");
        assertEquals("New Admin", back.getFullName());
        assertTrue(PasswordHasher.verify("NewPass1!", back.getPasswordHash()));
        assertEquals("Edited", saved.audit().all().get(0).getAction());

        assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "   ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "NewPass1!", "NextPass1!", null));
        assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "wrong", "NextPass1!", "NextPass1!"));
        assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "NewPass1!", "NextPass1!", "Mismatch1!"));
    }

    @Test
    void onlyAdminsCanUseTheNewActions() {
        User alice = data.users().find("alice");
        Listing listing = data.listings().find("L101");
        assertThrows(SecurityException.class, () -> admin.addAccount(alice, UserRole.BUYER,
                "sneaky", "Sneaky", "Password1!", null, null, null));
        assertThrows(SecurityException.class, () -> admin.softDeleteAccount(alice, data.users().find("bob")));
        assertThrows(SecurityException.class, () -> admin.editListing(alice, listing, "Hacked", "",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "1", null));
        assertThrows(SecurityException.class, () -> admin.updateProfile(alice, "Alice", null, null, null));
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertFalse(data.users().find("bob").isDeleted());
        assertTrue(data.audit().all().isEmpty());
    }

    @Test
    void editingALendListingKeepsItsLendingDurationRule() {
        Listing calculator = data.listings().find("L103");
        admin.editListing(adminUser, calculator, "TI-84", "Lend for a month",
                Category.ELECTRONICS, Condition.LIKE_NEW, Location.STUDENT_CENTRE, null, Duration.ofDays(30));

        com.hkust.marketplace.models.LendListing back = assertInstanceOf(
                com.hkust.marketplace.models.LendListing.class,
                DummyDataService.open(db).listings().find("L103"));
        assertEquals(Duration.ofDays(30), back.getLendingDuration());
        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, calculator,
                "TI-84", "Too short", Category.ELECTRONICS, Condition.LIKE_NEW,
                Location.STUDENT_CENTRE, null, Duration.ofSeconds(1)));
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
