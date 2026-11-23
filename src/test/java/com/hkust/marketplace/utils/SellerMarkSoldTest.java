package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerMarkSoldTest {

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
    void liveSaleWithPendingOfferBecomesSoldDeclinesOfferNotifiesBuyerAndSurvivesReload() {
        Listing charger = data.listings().find("L101");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        int dealsBefore = data.deals().allDeals().size();
        int noticesBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();
        assertEquals(ListingStatus.LIVE, charger.getStatus());

        seller.markSold(charlie, charger);

        assertEquals(ListingStatus.SOLD, charger.getStatus());
        assertEquals(RequestStatus.DECLINED, offer.getStatus());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.SOLD, saved.listings().find("L101").getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(offer.getId()).getStatus());
        List<Notification> notices = saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE);
        assertEquals(noticesBefore + 1, notices.size());
        assertTrue(notices.stream().anyMatch(n -> n.getMessage().contains("marked as sold")));
        assertEquals(NotificationRef.Target.LISTING, notices.get(0).getRelatedRef().getTarget());
        assertEquals("L101", notices.get(0).getRelatedRef().getId());
        assertEquals(dealsBefore, saved.deals().allDeals().size());
        assertTrue(saved.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void counteredOfferIsAlsoClosedAndBuyerNotified() {
        Listing charger = data.listings().find("L101");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("180"));
        assertEquals(RequestStatus.COUNTERED, offer.getStatus());

        seller.markSold(charlie, charger);

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.SOLD, saved.listings().find("L101").getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(offer.getId()).getStatus());
        assertTrue(saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .anyMatch(n -> n.getMessage().contains("marked as sold")));
    }

    @Test
    void liveSaleWithoutOffersStillPersistsSoldWithoutFabricatingADeal() {
        Listing charger = data.listings().find("L101");
        int dealsBefore = data.deals().allDeals().size();
        int noticesBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        seller.markSold(charlie, charger);

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.SOLD, saved.listings().find("L101").getStatus());
        assertEquals(dealsBefore, saved.deals().allDeals().size());
        assertEquals(noticesBefore, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void lendAndGiveawayListingsAreRefusedWithNoChanges() {
        Listing calculator = data.listings().find("L103");
        DealRequest borrow = data.deals().openRequestsByBuyer("bob").get(0);
        Listing freshGiveaway = new GiveawayListing((Seller) diana, "Free Plant", "Healthy, large pot.",
                Category.OTHERS, Condition.USED, Location.UG_HALL_V);
        freshGiveaway.setStatus(ListingStatus.LIVE);
        data.listings().add(freshGiveaway);

        IllegalStateException lendError = assertThrows(IllegalStateException.class,
                () -> seller.markSold(diana, calculator));
        assertEquals(SellerService.NOT_SALE_LISTING, lendError.getMessage());
        IllegalStateException giveawayError = assertThrows(IllegalStateException.class,
                () -> seller.markSold(diana, freshGiveaway));
        assertEquals(SellerService.NOT_SALE_LISTING, giveawayError.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find("L103").getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(borrow.getId()).getStatus());
        assertEquals(ListingStatus.LIVE, saved.listings().find(freshGiveaway.getId()).getStatus());
        assertTrue(saved.notifications().forUser("diana", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void reservedSaleIsRefusedWithNoChanges() {
        Listing textbook = data.listings().find("L100");
        DealRequest accepted = data.deals().lifecycle().requestsFor(textbook).get(0);
        assertEquals(ListingStatus.RESERVED, textbook.getStatus());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.markSold(charlie, textbook));
        assertEquals(SellerService.NOT_LIVE, ex.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.RESERVED, saved.listings().find("L100").getStatus());
        assertEquals(RequestStatus.ACCEPTED, saved.deals().findRequest(accepted.getId()).getStatus());
        assertTrue(saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .noneMatch(n -> n.getMessage().contains("marked as sold")));
    }

    @Test
    void otherNonLiveStatusesAreRefusedWithTheNotLiveMessage() {
        Listing fridge = data.listings().find("L104");
        ListingStatus original = fridge.getStatus();
        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.LENT,
                ListingStatus.GIVEN, ListingStatus.TAKEN_DOWN, ListingStatus.PENDING_REVIEW)) {
            fridge.setStatus(status);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> seller.markSold(diana, fridge));
            assertEquals(SellerService.NOT_LIVE, ex.getMessage());
            fridge.setStatus(original);
        }

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(original, saved.listings().find("L104").getStatus());
    }

    @Test
    void otherSellerBuyerAndAdminAreRefusedWithNoChanges() {
        Listing charger = data.listings().find("L101");
        for (User caller : List.of(diana, alice, adminUser)) {
            assertThrows(SecurityException.class, () -> seller.markSold(caller, charger));
        }

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus());
        assertTrue(saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .noneMatch(n -> n.getMessage().contains("marked as sold")));
    }

    @Test
    void deletedListingIsRefusedWithNoChanges() {
        Listing charger = data.listings().find("L101");
        charger.setDeleted(true);
        data.listings().update(charger);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.markSold(charlie, charger));
        assertEquals(SellerService.NOT_CHANGEABLE, ex.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        assertTrue(saved.listings().find("L101").isDeleted());
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus());
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

        assertThrows(RuntimeException.class, () -> seller.markSold(charlie, charger));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find("L101").getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(first.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(second.getId()).getStatus());
        assertFalse(saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .anyMatch(n -> n.getMessage().contains("marked as sold")));
    }
}
