package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Role checks in the service layer: a buyer or seller session can't run admin actions (#60). */
class RoleChecksTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final AdminService admin = new AdminService(data);
    private final SellerService seller = new SellerService(data);

    private final User adminUser = data.users().find("admin");
    private final User alice = data.users().find("alice");
    private final User charlie = data.users().find("charlie");
    private final User diana = data.users().find("diana");

    @Test
    void accessControlNeedsAnActiveAccountWithAnAllowedRole() {
        assertSame(adminUser, AccessControl.require(adminUser, UserRole.ADMIN));
        assertSame(alice, AccessControl.require(alice, UserRole.BUYER, UserRole.SELLER));
        assertThrows(SecurityException.class, () -> AccessControl.require(null, UserRole.ADMIN));
        assertThrows(SecurityException.class, () -> AccessControl.require(alice, UserRole.ADMIN));

        Buyer inactive = new Buyer("frank", "Frank", "hash", UserRole.BUYER);
        inactive.setActive(false);
        assertThrows(SecurityException.class, () -> AccessControl.require(inactive, UserRole.BUYER));
        Buyer deleted = new Buyer("gina", "Gina", "hash", UserRole.BUYER);
        deleted.setDeleted(true);
        assertThrows(SecurityException.class, () -> AccessControl.require(deleted, UserRole.BUYER));
    }

    @Test
    void buyersAndSellersCannotRunAnyAdminActionAndNothingChanges() {
        Listing pending = data.listings().find("L104");
        Listing live = data.listings().find("L101");
        Review review = data.reviews().all().get(0);
        User bob = data.users().find("bob");
        int auditBefore = data.audit().all().size();

        for (User notAdmin : List.of(alice, charlie)) {
            List<Executable> actions = List.of(
                    () -> admin.approveListing(notAdmin, pending),
                    () -> admin.rejectListing(notAdmin, pending, "Blurry"),
                    () -> admin.takeDownListing(notAdmin, live, "Spam"),
                    () -> admin.setActive(notAdmin, bob, false),
                    () -> admin.deactivateAccount(notAdmin, bob),
                    () -> admin.setReviewHidden(notAdmin, review, true));
            for (Executable action : actions) {
                assertThrows(SecurityException.class, action);
            }
        }

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.PENDING_REVIEW, saved.listings().find("L104").getStatus());
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus());
        assertTrue(saved.users().find("bob").isActive());
        assertFalse(saved.reviews().all().get(0).isHidden());
        assertEquals(auditBefore, saved.audit().all().size());
    }

    @Test
    void adminActionsSaveNotifyAndAudit() {
        admin.approveListing(adminUser, data.listings().find("L104"));
        admin.takeDownListing(adminUser, data.listings().find("L101"), "Counterfeit");
        admin.deactivateAccount(adminUser, data.users().find("bob"));
        admin.setReviewHidden(adminUser, data.reviews().all().get(0), true);

        DummyDataService saved = DummyDataService.open(db);
        Listing fridge = saved.listings().find("L104");
        assertEquals(ListingStatus.LIVE, fridge.getStatus());
        assertNotNull(fridge.getReviewedAt());
        assertEquals("Listing Approved", saved.notifications().forUser("diana").get(0).getTitle());
        assertEquals(ListingStatus.TAKEN_DOWN, saved.listings().find("L101").getStatus());
        assertEquals("Counterfeit", saved.listings().find("L101").getTakedownReason());
        assertFalse(saved.users().find("bob").isActive());
        assertTrue(saved.reviews().all().get(0).isHidden());
        assertEquals(List.of("Took down", "Deleted account"),
                saved.audit().all().stream().map(e -> e.getAction()).toList());
    }

    @Test
    void rejectNeedsAReasonAndAPendingListing() {
        Listing pending = data.listings().find("L104");
        assertThrows(IllegalArgumentException.class, () -> admin.rejectListing(adminUser, pending, "  "));
        assertThrows(IllegalStateException.class,
                () -> admin.approveListing(adminUser, data.listings().find("L101")));

        admin.rejectListing(adminUser, pending, "Blurry photos");
        assertEquals("Blurry photos", DummyDataService.open(db).listings().find("L104").getRejectionReason());
    }

    @Test
    void sellersDeleteOnlyTheirOwnUntouchedListings() {
        Listing dianasFridge = data.listings().find("L104");
        assertThrows(SecurityException.class, () -> seller.deleteListing(charlie, dianasFridge));
        assertThrows(SecurityException.class, () -> seller.deleteListing(alice, dianasFridge));
        assertThrows(SecurityException.class, () -> seller.deleteListing(adminUser, dianasFridge));
        assertNotNull(data.listings().find("L104"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> seller.deleteListing(diana, data.listings().find("L103")));
        assertEquals(SellerService.CANNOT_DELETE, refused.getMessage());

        seller.deleteListing(diana, dianasFridge);
        assertNull(DummyDataService.open(db).listings().find("L104"));
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
