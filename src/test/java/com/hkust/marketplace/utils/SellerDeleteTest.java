package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerDeleteTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerService seller;
    private User adminUser;
    private User alice;
    private User bob;
    private User charlie;
    private User diana;

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
        seller = new SellerService(data);
        adminUser = data.users().find("admin");
        alice = data.users().find("alice");
        bob = data.users().find("bob");
        charlie = data.users().find("charlie");
        diana = data.users().find("diana");
    }

    @Test
    void pendingOfferIsDeclinedBuyerNotifiedAndListingHiddenButResolvable() {
        Listing calculator = data.listings().find("L103");
        DealRequest request = data.deals().openRequestsByBuyer("bob").get(0);
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
        assertEquals(RequestStatus.PENDING, request.getStatus());

        seller.deleteListing(diana, calculator);

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(request.getId()).getStatus());

        List<Notification> notices = saved.notifications().forUser("bob", NotificationCategory.LISTING_REMOVED);
        assertEquals(1, notices.size());
        assertTrue(notices.get(0).getMessage().contains("TI-84"));
        assertEquals(NotificationRef.Target.LISTING, notices.get(0).getRelatedRef().getTarget());
        assertEquals("L103", notices.get(0).getRelatedRef().getId());

        Listing back = saved.listings().find("L103");
        assertNotNull(back);
        assertTrue(back.isDeleted());
        assertFalse(saved.listings().bySeller("diana").stream().anyMatch(l -> l.getId().equals("L103")));
        assertFalse(saved.listings().all().stream().anyMatch(l -> l.getId().equals("L103")));
    }

    @Test
    void reservedListingIsRefusedWithNoChanges() {
        Listing lamp = data.listings().find("L102");
        DealRequest claim = data.deals().openRequestsByBuyer("alice").get(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.deleteListing(diana, lamp));
        assertEquals(SellerService.NOT_CHANGEABLE, ex.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        assertFalse(saved.listings().find("L102").isDeleted());
        assertEquals(ListingStatus.RESERVED, saved.listings().find("L102").getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(claim.getId()).getStatus());
        assertTrue(saved.notifications().forUser("alice", NotificationCategory.LISTING_REMOVED).isEmpty());
    }

    @Test
    void soldLentGivenAndTakenDownListingsAreRefused() {
        Listing fridge = data.listings().find("L104");
        ListingStatus original = fridge.getStatus();
        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.LENT,
                ListingStatus.GIVEN, ListingStatus.TAKEN_DOWN)) {
            fridge.setStatus(status);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> seller.deleteListing(diana, fridge));
            assertEquals(SellerService.NOT_CHANGEABLE, ex.getMessage());
            fridge.setStatus(original);
        }

        DummyDataService saved = DummyDataService.open(db);
        assertFalse(saved.listings().find("L104").isDeleted());
        assertEquals(original, saved.listings().find("L104").getStatus());
    }

    @Test
    void otherSellerBuyerAndAdminAreRefusedWithNoChanges() {
        Listing fridge = data.listings().find("L104");
        for (User caller : List.of(charlie, alice, adminUser)) {
            assertThrows(SecurityException.class, () -> seller.deleteListing(caller, fridge));
        }

        DummyDataService saved = DummyDataService.open(db);
        assertFalse(saved.listings().find("L104").isDeleted());
        assertTrue(saved.notifications().forUser("diana", NotificationCategory.LISTING_REMOVED).isEmpty());
        assertFalse(saved.deals().openRequestsByBuyer("bob").isEmpty());
    }

    @Test
    void untouchedListingIsFlaggedAndHiddenAfterReload() {
        Listing fridge = data.listings().find("L104");

        seller.deleteListing(diana, fridge);

        DummyDataService saved = DummyDataService.open(db);
        Listing back = saved.listings().find("L104");
        assertNotNull(back);
        assertTrue(back.isDeleted());
        assertFalse(saved.listings().bySeller("diana").stream().anyMatch(l -> l.getId().equals("L104")));
        assertFalse(saved.listings().all().stream().anyMatch(l -> l.getId().equals("L104")));
        assertTrue(saved.notifications().forUser("diana", NotificationCategory.LISTING_REMOVED).isEmpty());
    }

    @Test
    void forcedFailureLeavesListingRequestsAndNotificationsUnchanged() {
        Listing charger = data.listings().find("L101");
        DealRequest first = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        DealRequest second = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        assertEquals(RequestStatus.PENDING, first.getStatus());
        assertEquals(RequestStatus.PENDING, second.getStatus());

        AtomicInteger calls = new AtomicInteger();
        data.deals().lifecycle().setListener(l -> {
            if (calls.incrementAndGet() == 2) {
                throw new RuntimeException("forced failure during decline");
            }
        });

        assertThrows(RuntimeException.class, () -> seller.deleteListing(charlie, charger));

        DummyDataService saved = DummyDataService.open(db);
        assertFalse(saved.listings().find("L101").isDeleted());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(first.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(second.getId()).getStatus());
        assertTrue(saved.notifications().forUser("alice", NotificationCategory.LISTING_REMOVED).isEmpty());
        assertTrue(saved.notifications().forUser("bob", NotificationCategory.LISTING_REMOVED).isEmpty());
    }
}
