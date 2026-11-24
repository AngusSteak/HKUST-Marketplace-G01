package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerAcceptRequestTest {

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

    @Test
    void acceptCreatesDealReservesListingDeclinesAllOtherOpenRequestsAndNotifiesEachBuyerOnce() {
        Listing charger = liveSale("Charger");
        PriceOffer accepted = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        PriceOffer bobCountered = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        data.deals().lifecycle().counterOffer(bobCountered, new BigDecimal("180"));
        DealRequest aliceOther = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("165"));
        int dealsBefore = data.deals().allDeals().size();
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();
        int bobBefore = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size();

        Deal deal = seller.acceptRequest(charlie, accepted);

        assertNotNull(deal);
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals(accepted.getId(), deal.getRequest().getId());
        assertEquals(RequestStatus.ACCEPTED, accepted.getStatus());
        assertEquals(ListingStatus.RESERVED, charger.getStatus());
        assertSame(deal, data.deals().lifecycle().activeDeal(charger).orElseThrow());
        assertEquals(RequestStatus.DECLINED, bobCountered.getStatus());
        assertEquals(RequestStatus.DECLINED, aliceOther.getStatus());

        List<Notification> aliceNotices = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE);
        List<Notification> bobNotices = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE);
        assertEquals(aliceBefore + 1, aliceNotices.size());
        assertEquals(bobBefore + 1, bobNotices.size());
        assertEquals(1, aliceNotices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.DEAL
                        && deal.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(aliceNotices.stream().noneMatch(
                n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST));
        assertEquals(1, bobNotices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && bobCountered.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.RESERVED, saved.listings().find(charger.getId()).getStatus());
        assertEquals(RequestStatus.ACCEPTED, saved.deals().findRequest(accepted.getId()).getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(bobCountered.getId()).getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(aliceOther.getId()).getStatus());
        assertEquals(dealsBefore + 1, saved.deals().allDeals().size());
        assertEquals(1, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.DEAL
                        && deal.getId().equals(n.getRelatedRef().getId())).count());
        assertEquals(1, saved.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && bobCountered.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(saved.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void acceptingABorrowRequestMarksTheListingLentAndDeclinesOtherBorrows() {
        Listing loan = liveLend("Bike");
        LocalDateTime now = LocalDateTime.now();
        BorrowRequest accepted = data.deals().lifecycle().submitBorrow((Buyer) alice, loan,
                now.plusDays(1), now.plusDays(5));
        BorrowRequest other = data.deals().lifecycle().submitBorrow((Buyer) bob, loan,
                now.plusDays(2), now.plusDays(6));
        int bobBefore = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size();

        Deal deal = seller.acceptRequest(charlie, accepted);

        assertEquals(ListingStatus.LENT, loan.getStatus());
        assertEquals(DealType.LENT, deal.getDealType());
        assertEquals(RequestStatus.ACCEPTED, accepted.getStatus());
        assertEquals(RequestStatus.DECLINED, other.getStatus());
        List<Notification> bobNotices = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE);
        assertEquals(bobBefore + 1, bobNotices.size());
        assertEquals(1, bobNotices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && other.getId().equals(n.getRelatedRef().getId())).count());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LENT, saved.listings().find(loan.getId()).getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(other.getId()).getStatus());
    }

    @Test
    void aCounteredRequestCannotBeAccepted() {
        Listing charger = liveSale("Charger");
        PriceOffer countered = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        data.deals().lifecycle().counterOffer(countered, new BigDecimal("180"));
        int dealsBefore = data.deals().allDeals().size();
        int noticesBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        assertThrows(IllegalStateException.class, () -> seller.acceptRequest(charlie, countered));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find(charger.getId()).getStatus());
        assertEquals(RequestStatus.COUNTERED, saved.deals().findRequest(countered.getId()).getStatus());
        assertEquals(dealsBefore, saved.deals().allDeals().size());
        assertEquals(noticesBefore, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void foreignSellerBuyerAdminAndNullActorAreRefusedWithoutChanges() {
        Listing charger = liveSale("Charger");
        DealRequest pending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        int dealsBefore = data.deals().allDeals().size();

        for (User caller : List.of(diana, alice, adminUser)) {
            assertThrows(SecurityException.class, () -> seller.acceptRequest(caller, pending));
        }
        assertThrows(SecurityException.class, () -> seller.acceptRequest(null, pending));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find(charger.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(pending.getId()).getStatus());
        assertEquals(dealsBefore, saved.deals().allDeals().size());
        assertTrue(saved.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void aFailureMidwayRollsBackTheDealStatusesAndNotices() {
        Listing charger = liveSale("Charger");
        DealRequest pending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        int noticesBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        data.deals().lifecycle().setListener(l -> {
            throw new RuntimeException("forced failure during accept");
        });
        assertThrows(RuntimeException.class, () -> seller.acceptRequest(charlie, pending));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.LIVE, saved.listings().find(charger.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(pending.getId()).getStatus());
        assertEquals(noticesBefore, saved.notifications()
                .forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }
}
