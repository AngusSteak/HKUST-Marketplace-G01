package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerCounterRequestTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerService seller;
    private User alice;
    private User bob;
    private User charlie;
    private User diana;
    private User adminUser;

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
        seller = new SellerService(data);
        alice = data.users().find("alice");
        bob = data.users().find("bob");
        charlie = data.users().find("charlie");
        diana = data.users().find("diana");
        adminUser = data.users().find("admin");
    }

    private Listing liveSale(String title) {
        Listing listing = new SaleListing((Seller) charlie, title, "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("100"));
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    private Listing liveLend(String title) {
        Listing listing = new LendListing((Seller) charlie, title, "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Duration.ofDays(30));
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    private long offerUpdateNotices(String username) {
        return data.notifications().forUser(username, NotificationCategory.OFFER_UPDATE).size();
    }

    @Test
    void counteringAPriceOfferPersistsTheCounterPriceAndNotifiesTheBuyerOnce() {
        Listing charger = liveSale("Charger");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        long aliceBefore = offerUpdateNotices("alice");

        PriceOffer countered = seller.counterPriceOffer(charlie, offer, "180.50");

        assertEquals(RequestStatus.COUNTERED, countered.getStatus());
        assertEquals(new BigDecimal("180.50"), countered.getCounterPrice());
        assertEquals(new BigDecimal("180.50"), countered.agreedPrice());
        assertEquals(aliceBefore + 1, offerUpdateNotices("alice"));
        List<Notification> notices = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE);
        assertEquals(1, notices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && offer.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());

        DummyDataService saved = DummyDataService.open(db);
        PriceOffer reloaded = (PriceOffer) saved.deals().findRequest(offer.getId());
        assertEquals(RequestStatus.COUNTERED, reloaded.getStatus());
        assertEquals(new BigDecimal("180.50"), reloaded.getCounterPrice());
        assertEquals(aliceBefore + 1, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void counteringABorrowRequestPersistsTheCounterPeriodAndNotifiesTheBuyerOnce() {
        Listing bike = liveLend("Bike");
        LocalDateTime now = LocalDateTime.now();
        BorrowRequest request = data.deals().lifecycle().submitBorrow((Buyer) alice, bike,
                now.plusDays(1), now.plusDays(5));
        LocalDateTime start = now.plusDays(2);
        LocalDateTime end = now.plusDays(10);
        long aliceBefore = offerUpdateNotices("alice");

        BorrowRequest countered = seller.counterBorrowRequest(charlie, request, start, end);

        assertEquals(RequestStatus.COUNTERED, countered.getStatus());
        assertEquals(start, countered.getCounterStart());
        assertEquals(end, countered.getCounterEnd());
        assertEquals(start, countered.agreedStart());
        assertEquals(end, countered.agreedEnd());
        assertEquals(aliceBefore + 1, offerUpdateNotices("alice"));
        assertEquals(1, data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && request.getId().equals(n.getRelatedRef().getId())).count());

        DummyDataService saved = DummyDataService.open(db);
        BorrowRequest reloaded = (BorrowRequest) saved.deals().findRequest(request.getId());
        assertEquals(RequestStatus.COUNTERED, reloaded.getStatus());
        assertEquals(start, reloaded.getCounterStart());
        assertEquals(end, reloaded.getCounterEnd());
        assertEquals(aliceBefore + 1, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void zeroNegativeAndOverTwoDecimalPricesThrowAndChangeNothing() {
        for (String bad : List.of("0", "-5", "10.123")) {
            Listing charger = liveSale("Charger " + bad);
            PriceOffer offer = data.deals().lifecycle()
                    .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
            long aliceBefore = offerUpdateNotices("alice");

            assertThrows(IllegalArgumentException.class,
                    () -> seller.counterPriceOffer(charlie, offer, bad));

            assertEquals(RequestStatus.PENDING, offer.getStatus());
            assertNull(offer.getCounterPrice());
            assertEquals(aliceBefore, offerUpdateNotices("alice"));
            DummyDataService saved = DummyDataService.open(db);
            PriceOffer reloaded = (PriceOffer) saved.deals().findRequest(offer.getId());
            assertEquals(RequestStatus.PENDING, reloaded.getStatus());
            assertNull(reloaded.getCounterPrice());
            assertEquals(aliceBefore, saved.notifications()
                    .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
        }
    }

    @Test
    void invalidBorrowPeriodsThrowAndChangeNothing() {
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime[]> badPeriods = List.of(
                new LocalDateTime[]{now.minusDays(1), now.plusDays(5)},
                new LocalDateTime[]{now.plusDays(5), now.plusDays(2)},
                new LocalDateTime[]{now.plusDays(1), now.plusDays(40)},
                new LocalDateTime[]{now.plusMinutes(1), now.plusMinutes(1).plusSeconds(2)});

        for (int i = 0; i < badPeriods.size(); i++) {
            Listing bike = liveLend("Bike " + i);
            BorrowRequest request = data.deals().lifecycle().submitBorrow((Buyer) alice, bike,
                    now.plusDays(1), now.plusDays(5));
            long aliceBefore = offerUpdateNotices("alice");
            LocalDateTime start = badPeriods.get(i)[0];
            LocalDateTime end = badPeriods.get(i)[1];

            assertThrows(IllegalArgumentException.class,
                    () -> seller.counterBorrowRequest(charlie, request, start, end));

            assertEquals(RequestStatus.PENDING, request.getStatus());
            assertNull(request.getCounterStart());
            assertNull(request.getCounterEnd());
            assertEquals(aliceBefore, offerUpdateNotices("alice"));
            DummyDataService saved = DummyDataService.open(db);
            BorrowRequest reloaded = (BorrowRequest) saved.deals().findRequest(request.getId());
            assertEquals(RequestStatus.PENDING, reloaded.getStatus());
            assertNull(reloaded.getCounterStart());
            assertNull(reloaded.getCounterEnd());
        }
    }

    @Test
    void aCounteredRequestCannotBeCounteredAgainOrAccepted() {
        Listing charger = liveSale("Charger");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        seller.counterPriceOffer(charlie, offer, "180");
        long aliceBefore = offerUpdateNotices("alice");
        int dealsBefore = data.deals().allDeals().size();

        assertThrows(IllegalStateException.class,
                () -> seller.counterPriceOffer(charlie, offer, "190"));
        assertThrows(IllegalStateException.class, () -> seller.acceptRequest(charlie, offer));

        assertEquals(RequestStatus.COUNTERED, offer.getStatus());
        assertEquals(new BigDecimal("180.00"), offer.getCounterPrice());
        assertEquals(aliceBefore, offerUpdateNotices("alice"));
        assertEquals(dealsBefore, data.deals().allDeals().size());
        assertEquals(ListingStatus.LIVE, charger.getStatus());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.COUNTERED, saved.deals().findRequest(offer.getId()).getStatus());
        assertEquals(dealsBefore, saved.deals().allDeals().size());
        assertEquals(aliceBefore, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void foreignSellerBuyerAdminAndNullActorAreRefusedWithoutChanges() {
        Listing charger = liveSale("Charger");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        Listing bike = liveLend("Bike");
        LocalDateTime now = LocalDateTime.now();
        BorrowRequest borrow = data.deals().lifecycle().submitBorrow((Buyer) bob, bike,
                now.plusDays(1), now.plusDays(5));
        long aliceBefore = offerUpdateNotices("alice");
        long bobBefore = offerUpdateNotices("bob");

        for (User caller : List.of(diana, alice, adminUser)) {
            assertThrows(SecurityException.class, () -> seller.counterPriceOffer(caller, offer, "180"));
            assertThrows(SecurityException.class, () -> seller.counterBorrowRequest(caller, borrow,
                    now.plusDays(2), now.plusDays(6)));
        }
        assertThrows(SecurityException.class, () -> seller.counterPriceOffer(null, offer, "180"));
        assertThrows(SecurityException.class,
                () -> seller.counterBorrowRequest(null, borrow, now.plusDays(2), now.plusDays(6)));

        assertEquals(RequestStatus.PENDING, offer.getStatus());
        assertEquals(RequestStatus.PENDING, borrow.getStatus());
        assertEquals(aliceBefore, offerUpdateNotices("alice"));
        assertEquals(bobBefore, offerUpdateNotices("bob"));
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void aFailureMidwayRollsBackTheCounterStatusAndNotice() {
        Listing charger = liveSale("Charger");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        long aliceBefore = offerUpdateNotices("alice");

        data.deals().lifecycle().setListener(l -> {
            throw new RuntimeException("forced failure during counter");
        });
        assertThrows(RuntimeException.class, () -> seller.counterPriceOffer(charlie, offer, "180"));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(offer.getId()).getStatus());
        assertNull(((PriceOffer) saved.deals().findRequest(offer.getId())).getCounterPrice());
        assertEquals(aliceBefore, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }
}
