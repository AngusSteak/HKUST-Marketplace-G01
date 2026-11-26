package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.forms.PhotoDraft;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The less common paths of the admin actions (SRS UC-3.3, UC-3.4, UC-3.7): photo rules when
 * editing a listing, rollbacks when the database refuses a write, and every account type in
 * the account actions. Seed data: L101 is charlie's live sale, L103 diana's live lend item,
 * L102 diana's reserved giveaway (alice's pending claim Q2), L104 diana's listing pending
 * review, and Q4 bob's pending borrow of L103.
 */
class AdminServiceBranchTest {

    @TempDir
    Path temp;

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final AdminService admin = new AdminService(data);
    private final User adminUser = data.users().find("admin");

    // ---------- Edit listing (UC-3.7) ----------

    @Test
    void editingALendListingChangesItsDuration() {
        LendListing calculator = (LendListing) data.listings().find("L103");

        admin.editListing(adminUser, calculator, "TI-84 Plus", "Graphing calculator", Category.ELECTRONICS,
                Condition.LIKE_NEW, Location.LIBRARY, null, Duration.ofDays(3));

        LendListing back = (LendListing) DummyDataService.open(db).listings().find("L103");
        assertEquals("TI-84 Plus", back.getTitle());
        assertEquals(Duration.ofDays(3), back.getLendingDuration());
    }

    @Test
    void nullPhotoListsKeepTheListingsCurrentPhotos() {
        Listing charger = data.listings().find("L101");
        charger.setPhotoPaths(List.of("data/photos/charlie/L101/a.jpg"));

        admin.editListing(adminUser, charger, "Charger", "Works fine", Category.ELECTRONICS, Condition.USED,
                Location.LIBRARY, "200", null, null, null, null);

        assertEquals(List.of("data/photos/charlie/L101/a.jpg"), charger.getPhotoPaths());
    }

    @Test
    void newJpegAndPngPhotosAreStoredInAnyLetterCase() {
        Listing charger = data.listings().find("L101");
        PhotoStorage photos = (source, username, listingId) -> "stored/" + source.getFileName();

        admin.editListing(adminUser, charger, "Charger", "Works fine", Category.ELECTRONICS, Condition.USED,
                Location.LIBRARY, "200", null, List.of(),
                List.of(draft("a.JPEG"), draft("b.png"), draft("c.jpg")), photos);

        assertEquals(List.of("stored/a.JPEG", "stored/b.png", "stored/c.jpg"), charger.getPhotoPaths());
    }

    @Test
    void badPhotoInputIsRefusedBeforeAnythingChanges() {
        Listing charger = data.listings().find("L101");
        PhotoStorage photos = (source, username, listingId) -> "stored/" + source.getFileName();

        assertEditRefused(charger, Arrays.asList("ok.jpg", " "), List.of(), photos);
        assertEditRefused(charger, List.of(), List.of(new PhotoDraft(null, "missing")), photos);
        assertEditRefused(charger, List.of(), List.of(draft("README")), photos);
        assertEditRefused(charger, List.of(), List.of(draft("photo.gif")), photos);
        assertEditRefused(charger, List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg"),
                List.of(draft("6.jpg"), draft("7.jpg")), photos);
        assertEquals("MacBook Pro Charger 61W", charger.getTitle());
    }

    @Test
    void addingPhotosNeedsAPhotoStore() {
        Listing charger = data.listings().find("L101");
        assertThrows(NullPointerException.class, () -> admin.editListing(adminUser, charger, "Charger",
                "Works fine", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "200", null,
                List.of(), List.of(draft("a.jpg")), null));
    }

    @Test
    void missingTextOrAnInvalidPriceIsRefused() {
        Listing charger = data.listings().find("L101");
        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, charger, null,
                "desc", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "200", null));
        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, charger, "Title",
                "", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "200", null));
        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, charger, "Title",
                "desc", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "lots", null));
    }

    @Test
    void aFailedSaleEditRestoresTheListingAndDeletesNewPhotoFiles() throws IOException {
        SaleListing charger = (SaleListing) data.listings().find("L101");
        String oldPrice = charger.getPrice().getDisplay();
        Path stored = Files.createFile(temp.resolve("stored.jpg"));
        PhotoStorage photos = (source, username, listingId) -> stored.toString();
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.editListing(adminUser, charger, "Changed",
                "Changed", Category.OTHERS, Condition.NEW, Location.NORTH_GATE, "999", null,
                List.of(), List.of(draft("new.jpg")), photos));

        assertEquals("MacBook Pro Charger 61W", charger.getTitle());
        assertEquals(oldPrice, charger.getPrice().getDisplay());
        assertTrue(charger.getPhotoPaths().isEmpty());
        assertFalse(Files.exists(stored), "the photo stored for the failed edit is removed");
    }

    @Test
    void aFailedLendEditRestoresTheDuration() {
        LendListing calculator = (LendListing) data.listings().find("L103");
        Duration before = calculator.getLendingDuration();
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.editListing(adminUser, calculator, "Changed",
                "Changed", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, null, Duration.ofDays(1)));

        assertEquals(before, calculator.getLendingDuration());
        assertEquals("Graphing Calculator TI-84", calculator.getTitle());
    }

    @Test
    void aFailedGiveawayEditRestoresTheText() {
        Listing lamp = data.listings().find("L102");
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.editListing(adminUser, lamp, "Changed",
                "Changed", Category.OTHERS, Condition.USED, Location.LIBRARY, null, null));

        assertEquals("IKEA Desk Lamp", lamp.getTitle());
    }

    // ---------- Reports (UC-3.7 A6) ----------

    @Test
    void dismissingNeedsANoteAndAnOpenReport() {
        Listing charger = data.listings().find("L101");
        assertThrows(IllegalArgumentException.class, () -> admin.dismissReports(adminUser, charger, null));
        assertThrows(IllegalArgumentException.class, () -> admin.dismissReports(adminUser, charger, "  "));
        assertThrows(IllegalStateException.class, () -> admin.dismissReports(adminUser, charger, "Checked"));
    }

    // ---------- Add listing on behalf (UC-3.7) ----------

    @Test
    void addOnBehalfRefusesADeletedSellerAndAnInvalidForm() {
        Seller charlie = (Seller) data.users().find("charlie");
        PhotoStorage photos = (source, username, listingId) -> "unused";

        ListingFormState blank = new ListingFormState();
        blank.setType(ListingType.SELL);
        assertThrows(IllegalArgumentException.class,
                () -> admin.addListingOnBehalf(adminUser, charlie, blank, photos));

        charlie.setDeleted(true);
        assertThrows(IllegalStateException.class,
                () -> admin.addListingOnBehalf(adminUser, charlie, giveawayForm(), photos));
    }

    @Test
    void addOnBehalfStoresTheChosenPhotos() {
        Seller charlie = (Seller) data.users().find("charlie");
        ListingFormState form = giveawayForm();
        form.addChosenPhoto(draft("lamp.png"));
        PhotoStorage photos = (source, username, listingId) -> "stored/" + username + "/" + source.getFileName();

        Listing added = admin.addListingOnBehalf(adminUser, charlie, form, photos);

        assertEquals(List.of("stored/charlie/lamp.png"), added.getPhotoPaths());
    }

    @Test
    void aFailedAddOnBehalfDeletesItsStoredPhotos() throws IOException {
        Seller charlie = (Seller) data.users().find("charlie");
        ListingFormState form = giveawayForm();
        form.addChosenPhoto(draft("lamp.png"));
        Path stored = Files.createFile(temp.resolve("lamp.png"));
        PhotoStorage photos = (source, username, listingId) -> stored.toString();
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.addListingOnBehalf(adminUser, charlie, form, photos));

        assertFalse(Files.exists(stored));
    }

    // ---------- Accounts (UC-3.3, UC-3.4) ----------

    @Test
    void addedAccountsTurnBlankOptionalFieldsIntoNothing() {
        Buyer buyer = (Buyer) admin.addAccount(adminUser, UserRole.BUYER, "erin", "Erin Ma",
                "Password1!", "   ", null, null);
        Seller seller = (Seller) admin.addAccount(adminUser, UserRole.SELLER, "fred", "Fred Ng",
                "Password1!", null, null, null);

        assertNull(buyer.getResidenceHall());
        assertNull(seller.getBio());
        assertTrue(seller.getPreferredMeetup().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> admin.addAccount(adminUser, UserRole.BUYER,
                null, "No Name", "Password1!", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> admin.addAccount(adminUser, UserRole.BUYER,
                "nameless", " ", "Password1!", null, null, null));
    }

    @Test
    void editingASellerChangesBioAndMeetupOnlyWhenGiven() {
        Seller charlie = (Seller) data.users().find("charlie");

        admin.updateAccount(adminUser, charlie, "Charlie L.", null, "Sells gadgets", Location.NORTH_GATE);
        assertEquals("Sells gadgets", charlie.getBio());
        assertEquals(EnumSet.of(Location.NORTH_GATE), charlie.getPreferredMeetup());

        admin.updateAccount(adminUser, charlie, "Charlie L.", null, " ", null);
        assertNull(charlie.getBio());
        assertEquals(EnumSet.of(Location.NORTH_GATE), charlie.getPreferredMeetup(), "a null meet-up keeps it");
    }

    @Test
    void aFailedAccountEditRestoresBuyerAndSellerFields() {
        Buyer alice = (Buyer) data.users().find("alice");
        Seller charlie = (Seller) data.users().find("charlie");
        String hall = alice.getResidenceHall();
        String bio = charlie.getBio();
        var meetup = EnumSet.copyOf(charlie.getPreferredMeetup().isEmpty()
                ? EnumSet.noneOf(Location.class) : charlie.getPreferredMeetup());
        failNotifications();

        assertThrows(DatabaseException.class,
                () -> admin.updateAccount(adminUser, alice, "Changed", "Hall 9", null, null));
        assertThrows(DatabaseException.class,
                () -> admin.updateAccount(adminUser, charlie, "Changed", null, "New bio", Location.SOUTH_GATE));

        assertEquals("Alice Chan", alice.getFullName());
        assertEquals(hall, alice.getResidenceHall());
        assertEquals("Charlie Lee", charlie.getFullName());
        assertEquals(bio, charlie.getBio());
        assertEquals(meetup, charlie.getPreferredMeetup());
    }

    @Test
    void activationIgnoresNoChangeAndRefusesADeletedAccount() {
        User bob = data.users().find("bob");
        int auditBefore = data.audit().all().size();

        admin.setActive(adminUser, bob, true);
        assertEquals(auditBefore, data.audit().all().size(), "already active: nothing is written");

        bob.setDeleted(true);
        assertThrows(IllegalStateException.class, () -> admin.setActive(adminUser, bob, false));
    }

    @Test
    void aFailedDeactivationKeepsTheAccountActive() {
        User bob = data.users().find("bob");
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.setActive(adminUser, bob, false));

        assertTrue(bob.isActive());
    }

    // ---------- Soft delete (UC-3.3) ----------

    @Test
    void deletingASellerClosesEveryOpenRequestDealAndListing() {
        Seller diana = (Seller) data.users().find("diana");
        Listing calculator = data.listings().find("L103");
        // Bob's pending borrow is accepted, so L103 is Lent with an active deal.
        DealRequest borrow = data.deals().lifecycle().findRequest("Q4");
        var loan = data.deals().lifecycle().accept(borrow);
        assertEquals(ListingStatus.LENT, calculator.getStatus());

        admin.softDeleteAccount(adminUser, diana);

        assertTrue(diana.isDeleted());
        assertFalse(diana.isActive());
        assertNull(diana.getBio());
        assertTrue(diana.getPreferredMeetup().isEmpty());
        for (String id : List.of("L102", "L103", "L104")) {
            Listing listing = data.listings().find(id);
            assertEquals(ListingStatus.TAKEN_DOWN, listing.getStatus(), id);
            assertEquals(AdminService.ACCOUNT_DELETED_REASON, listing.getTakedownReason(), id);
        }
        assertEquals(RequestStatus.DECLINED, data.deals().lifecycle().findRequest("Q2").getStatus());
        assertEquals(DealStatus.CANCELLED, loan.getStatus());
        // Buyers whose request or deal closed hear about it as an offer update.
        assertFalse(data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).isEmpty());
        assertFalse(data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void deletingABuyerDeclinesTheirCounteredOfferAndTellsTheSeller() {
        Buyer bob = (Buyer) data.users().find("bob");
        Listing charger = data.listings().find("L101");
        PriceOffer offer = data.deals().lifecycle().submitOffer(bob, charger, new BigDecimal("200"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("230"));

        admin.softDeleteAccount(adminUser, bob);

        assertEquals(RequestStatus.DECLINED, offer.getStatus());
        assertNull(bob.getResidenceHall());
        assertEquals(ListingStatus.LIVE, charger.getStatus(), "the seller's listing stays live");
        assertFalse(data.notifications().forUser("charlie", NotificationCategory.ANNOUNCEMENT).isEmpty());
        assertThrows(IllegalStateException.class, () -> admin.softDeleteAccount(adminUser, bob));
    }

    @Test
    void aFailedSellerDeleteRestoresTheAccountAndListings() {
        Seller charlie = (Seller) data.users().find("charlie");
        charlie.setBio("Sells gadgets");
        charlie.setPreferredMeetup(EnumSet.of(Location.LIBRARY));
        List<ListingStatus> before = new ArrayList<>();
        data.listings().bySeller("charlie").forEach(l -> before.add(l.getStatus()));
        // charlie has an active deal with alice (D1), so a notice is sent and fails.
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.softDeleteAccount(adminUser, charlie));

        assertFalse(charlie.isDeleted());
        assertTrue(charlie.isActive());
        assertEquals("Charlie Lee", charlie.getFullName());
        assertEquals("Sells gadgets", charlie.getBio());
        assertEquals(EnumSet.of(Location.LIBRARY), charlie.getPreferredMeetup());
        List<ListingStatus> after = new ArrayList<>();
        data.listings().bySeller("charlie").forEach(l -> after.add(l.getStatus()));
        assertEquals(before, after);
    }

    @Test
    void aFailedBuyerDeleteRestoresTheResidenceHall() {
        Buyer alice = (Buyer) data.users().find("alice");
        alice.setResidenceHall("Hall 5");
        failNotifications();

        assertThrows(DatabaseException.class, () -> admin.softDeleteAccount(adminUser, alice));

        assertFalse(alice.isDeleted());
        assertEquals("Hall 5", alice.getResidenceHall());
        assertEquals("Alice Chan", alice.getFullName());
    }

    // ---------- Admin's own profile ----------

    @Test
    void anyPasswordFieldStartsAPasswordChangeThatNeedsAllThree() {
        for (String[] fields : new String[][]{
                {"admin123", "", ""}, {"", "NewPass1!", ""}, {"", "", "NewPass1!"}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> admin.updateProfile(adminUser, "Admin", fields[0], fields[1], fields[2]));
        }
    }

    @Test
    void passwordChangeChecksTheCurrentPasswordStrengthAndConfirmation() {
        assertEquals(ProfileService.CURRENT_PASSWORD_WRONG, assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "wrong", "NewPass1!", "NewPass1!")).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "admin123", "weak", "weak"));
        assertEquals(ProfileService.CONFIRMATION_MISMATCH, assertThrows(IllegalArgumentException.class,
                () -> admin.updateProfile(adminUser, "Admin", "admin123", "NewPass1!", "NewPass2!")).getMessage());
    }

    @Test
    void profileReportsOnlyTheFieldsThatChanged() {
        String name = adminUser.getFullName();

        assertTrue(admin.updateProfile(adminUser, name, null, null, null).isEmpty());
        assertEquals(List.of("password"), admin.updateProfile(adminUser, name, "admin123", "NewPass1!", "NewPass1!"));
        assertEquals(List.of("full name"), admin.updateProfile(adminUser, "Head Admin", "", "", ""));
        assertEquals(LoginGate.Outcome.OK, LoginGate.check(adminUser, UserRole.ADMIN, "NewPass1!"));
        assertThrows(IllegalArgumentException.class, () -> admin.updateProfile(adminUser, " ", "", "", ""));
    }

    @Test
    void aFailedProfileSaveRestoresNameAndPassword() {
        String hash = adminUser.getPasswordHash();
        db.update("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_log"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class,
                () -> admin.updateProfile(adminUser, "Renamed", "admin123", "NewPass1!", "NewPass1!"));

        assertEquals("HKUST Marketplace Admin", adminUser.getFullName());
        assertEquals(hash, adminUser.getPasswordHash());
    }

    // ---------- Helpers ----------

    private void assertEditRefused(Listing listing, List<String> kept, List<PhotoDraft> added, PhotoStorage photos) {
        assertThrows(IllegalArgumentException.class, () -> admin.editListing(adminUser, listing, "Changed",
                "Changed", Category.ELECTRONICS, Condition.USED, Location.LIBRARY, "200", null, kept, added, photos));
    }

    private static PhotoDraft draft(String name) {
        return new PhotoDraft(Path.of(name), name);
    }

    private static ListingFormState giveawayForm() {
        ListingFormState state = new ListingFormState();
        state.setType(ListingType.GIVEAWAY);
        state.setTitle("Desk lamp");
        state.setDescription("Free to a good home");
        state.setCategory(Category.FURNITURE);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.LIBRARY);
        return state;
    }

    private void failNotifications() {
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
