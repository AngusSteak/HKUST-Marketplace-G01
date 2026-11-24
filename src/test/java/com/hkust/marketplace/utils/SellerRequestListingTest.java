package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerRequestListingTest {

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

    @Test
    void onlyOpenRequestsOfTheActingSellerOnAvailableListingsAreReturnedGrouped() {
        Listing charger = data.listings().find("L101");
        DealRequest acceptedHistory = data.deals().lifecycle().requestsFor(data.listings().find("L100")).get(0);
        assertEquals(RequestStatus.ACCEPTED, acceptedHistory.getStatus());

        DealRequest pending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        PriceOffer countered = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        data.deals().lifecycle().counterOffer(countered, new BigDecimal("180"));
        DealRequest declined = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("165"));
        data.deals().lifecycle().decline(declined);

        Listing foreign = data.listings().find("L103");
        DealRequest foreignRequest = data.deals().lifecycle().submitBorrow((Buyer) alice, foreign,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10));

        LinkedHashMap<Listing, List<DealRequest>> groups = seller.pendingRequestsByListing(charlie);

        assertEquals(List.of(charger), new ArrayList<>(groups.keySet()));
        assertEquals(List.of(countered.getId(), pending.getId()),
                groups.get(charger).stream().map(DealRequest::getId).toList());
        assertEquals(RequestStatus.COUNTERED, groups.get(charger).get(0).getStatus());
        assertEquals(new BigDecimal("180.00"), ((PriceOffer) groups.get(charger).get(0)).getCounterPrice());
        assertFalse(groups.containsKey(data.listings().find("L100")));
        assertFalse(groups.containsValue(List.of(declined)));
        groups.values().forEach(list -> assertFalse(list.contains(declined)));
        groups.values().forEach(list -> assertFalse(list.contains(foreignRequest)));
        groups.values().forEach(list -> assertFalse(list.contains(acceptedHistory)));
    }

    @Test
    void groupsAreOrderedByNewestSubmissionAndRequestsWithinAGroupAreNewestFirst() {
        Listing a = liveSale("A");
        Listing b = liveSale("B");
        Listing c = liveSale("C");

        DealRequest a1 = data.deals().lifecycle().submitOffer((Buyer) alice, a, new BigDecimal("10"));
        DealRequest b1 = data.deals().lifecycle().submitOffer((Buyer) alice, b, new BigDecimal("10"));
        DealRequest a2 = data.deals().lifecycle().submitOffer((Buyer) bob, a, new BigDecimal("11"));
        DealRequest c1 = data.deals().lifecycle().submitOffer((Buyer) alice, c, new BigDecimal("10"));
        DealRequest b2 = data.deals().lifecycle().submitOffer((Buyer) bob, b, new BigDecimal("11"));

        LinkedHashMap<Listing, List<DealRequest>> groups = seller.pendingRequestsByListing(charlie);

        assertEquals(List.of(b, c, a), new ArrayList<>(groups.keySet()));
        assertEquals(List.of(b2.getId(), b1.getId()),
                groups.get(b).stream().map(DealRequest::getId).toList());
        assertEquals(List.of(c1.getId()),
                groups.get(c).stream().map(DealRequest::getId).toList());
        assertEquals(List.of(a2.getId(), a1.getId()),
                groups.get(a).stream().map(DealRequest::getId).toList());
    }

    @Test
    void deletedAndNonLiveListingsAreFilteredWhileRejectedIsStillLiveish() {
        Listing deleted = liveSale("Deleted");
        DealRequest deletedRequest = data.deals().lifecycle()
                .submitOffer((Buyer) alice, deleted, new BigDecimal("10"));
        deleted.setDeleted(true);
        data.listings().update(deleted);

        Listing takenDown = liveSale("Taken down");
        DealRequest takenDownRequest = data.deals().lifecycle()
                .submitOffer((Buyer) alice, takenDown, new BigDecimal("10"));
        takenDown.setStatus(ListingStatus.TAKEN_DOWN);
        data.listings().update(takenDown);

        Listing sold = liveSale("Sold");
        DealRequest soldRequest = data.deals().lifecycle()
                .submitOffer((Buyer) alice, sold, new BigDecimal("10"));
        sold.setStatus(ListingStatus.SOLD);
        data.listings().update(sold);

        Listing rejected = liveSale("Rejected");
        DealRequest rejectedRequest = data.deals().lifecycle()
                .submitOffer((Buyer) alice, rejected, new BigDecimal("10"));
        rejected.setStatus(ListingStatus.REJECTED);
        data.listings().update(rejected);

        LinkedHashMap<Listing, List<DealRequest>> groups = seller.pendingRequestsByListing(charlie);

        assertEquals(List.of(rejected), new ArrayList<>(groups.keySet()));
        assertEquals(List.of(rejectedRequest.getId()),
                groups.get(rejected).stream().map(DealRequest::getId).toList());
        assertTrue(groups.values().stream().flatMap(List::stream).noneMatch(deletedRequest::equals));
        assertTrue(groups.values().stream().flatMap(List::stream).noneMatch(takenDownRequest::equals));
        assertTrue(groups.values().stream().flatMap(List::stream).noneMatch(soldRequest::equals));

        assertEquals(List.of(rejectedRequest),
                seller.pendingRequestsForListing(charlie, rejected));
        assertTrue(seller.pendingRequestsForListing(charlie, deleted).isEmpty());
        assertTrue(seller.pendingRequestsForListing(charlie, takenDown).isEmpty());
    }

    @Test
    void buyerAdminAndForeignListingAreRefused() {
        Listing charger = data.listings().find("L101");
        data.deals().lifecycle().submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        Listing foreign = data.listings().find("L103");

        assertThrows(SecurityException.class, () -> seller.pendingRequestsByListing(alice));
        assertThrows(SecurityException.class, () -> seller.pendingRequestsByListing(adminUser));
        assertThrows(SecurityException.class, () -> seller.pendingRequestsByListing(null));
        assertThrows(SecurityException.class, () -> seller.pendingRequestsForListing(charlie, foreign));
        assertThrows(SecurityException.class, () -> seller.pendingRequestsForListing(bob, charger));

        assertEquals(1, seller.pendingRequestsByListing(charlie).get(charger).size());
    }

    @Test
    void resultSurvivesReloadAndIsImmutable() {
        Listing charger = data.listings().find("L101");
        DealRequest offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));

        LinkedHashMap<Listing, List<DealRequest>> groups = seller.pendingRequestsByListing(charlie);
        assertThrows(UnsupportedOperationException.class, () -> groups.get(charger).clear());

        DummyDataService saved = DummyDataService.open(db);
        LinkedHashMap<Listing, List<DealRequest>> afterReload =
                new SellerService(saved).pendingRequestsByListing(saved.users().find("charlie"));
        assertEquals(List.of(offer.getId()),
                afterReload.values().stream().flatMap(List::stream).map(DealRequest::getId).toList());
    }
}
