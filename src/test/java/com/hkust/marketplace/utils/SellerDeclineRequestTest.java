package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.GiveawayListing;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerDeclineRequestTest {

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

    private Listing liveGiveaway(String title) {
        Listing listing = new GiveawayListing((Seller) charlie, title, "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY);
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    @Test
    void decliningAPendingClaimRelistsTheGiveawayAndNotifiesTheBuyerOnce() {
        Listing giveaway = liveGiveaway("Free lamp");
        Claim claim = data.deals().lifecycle().submitClaim((Buyer) alice, giveaway);
        assertEquals(ListingStatus.RESERVED, giveaway.getStatus());
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        seller.declineRequest(charlie, claim);

        assertEquals(RequestStatus.DECLINED, claim.getStatus());
        assertEquals(ListingStatus.LIVE, giveaway.getStatus());
        List<Notification> notices = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE);
        assertEquals(aliceBefore + 1, notices.size());
        assertEquals(1, notices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && claim.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(claim.getId()).getStatus());
        assertEquals(ListingStatus.LIVE, saved.listings().find(giveaway.getId()).getStatus());
        assertEquals(1, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && claim.getId().equals(n.getRelatedRef().getId())).count());
        assertEquals(RequestStatus.PENDING,
                data.deals().lifecycle().submitClaim((Buyer) bob, giveaway).getStatus());
    }

    @Test
    void declineAllDeclinesOnlyThatListingsOpenRequestsAndNotifiesEachBuyerOnce() {
        Listing charger = liveSale("Charger");
        Listing other = liveSale("Other");
        DealRequest alicePending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        DealRequest bobPending = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        PriceOffer aliceCountered = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("165"));
        data.deals().lifecycle().counterOffer(aliceCountered, new BigDecimal("168"));
        DealRequest aliceDeclined = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("160"));
        data.deals().lifecycle().decline(aliceDeclined);
        DealRequest otherListingPending = data.deals().lifecycle()
                .submitOffer((Buyer) bob, other, new BigDecimal("90"));
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();
        int bobBefore = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size();

        seller.declineAllForListing(charlie, charger);

        assertEquals(RequestStatus.DECLINED, alicePending.getStatus());
        assertEquals(RequestStatus.DECLINED, bobPending.getStatus());
        assertEquals(RequestStatus.DECLINED, aliceCountered.getStatus());
        assertEquals(RequestStatus.DECLINED, aliceDeclined.getStatus());
        assertEquals(RequestStatus.PENDING, otherListingPending.getStatus());
        assertEquals(ListingStatus.LIVE, charger.getStatus());
        List<Notification> aliceNotices = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE);
        List<Notification> bobNotices = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE);
        assertEquals(aliceBefore + 1, aliceNotices.size());
        assertEquals(bobBefore + 1, bobNotices.size());
        assertEquals(1, aliceNotices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && List.of(alicePending.getId(), aliceCountered.getId())
                                .contains(n.getRelatedRef().getId())).count());
        assertEquals(1, bobNotices.stream()
                .filter(n -> n.getRelatedRef().getTarget() == NotificationRef.Target.REQUEST
                        && bobPending.getId().equals(n.getRelatedRef().getId())).count());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(alicePending.getId()).getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(bobPending.getId()).getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(aliceCountered.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(otherListingPending.getId()).getStatus());
        assertEquals(aliceBefore + 1, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size());
        assertEquals(bobBefore + 1, saved.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void aClosedRequestCannotBeDeclinedAndNothingChanges() {
        Listing charger = liveSale("Charger");
        DealRequest declined = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        data.deals().lifecycle().decline(declined);
        Listing acceptedOn = liveSale("Accepted on");
        DealRequest accepted = data.deals().lifecycle()
                .submitOffer((Buyer) bob, acceptedOn, new BigDecimal("120"));
        data.deals().lifecycle().accept(accepted);
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();
        int bobBefore = data.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size();

        assertThrows(IllegalStateException.class, () -> seller.declineRequest(charlie, declined));
        assertThrows(IllegalStateException.class, () -> seller.declineRequest(charlie, accepted));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(declined.getId()).getStatus());
        assertEquals(RequestStatus.ACCEPTED, saved.deals().findRequest(accepted.getId()).getStatus());
        assertEquals(aliceBefore, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size());
        assertEquals(bobBefore, saved.notifications().forUser("bob", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void foreignSellerBuyerAdminAndNullActorAreRefusedWithoutChanges() {
        Listing charger = liveSale("Charger");
        DealRequest pending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        Listing foreign = new SaleListing((Seller) diana, "Diana's", "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("100"));
        foreign.setStatus(ListingStatus.LIVE);
        data.listings().add(foreign);
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        for (User caller : List.of(diana, alice, adminUser)) {
            assertThrows(SecurityException.class, () -> seller.declineRequest(caller, pending));
            assertThrows(SecurityException.class, () -> seller.declineAllForListing(caller, charger));
        }
        assertThrows(SecurityException.class, () -> seller.declineRequest(null, pending));
        assertThrows(SecurityException.class, () -> seller.declineAllForListing(null, charger));
        assertThrows(SecurityException.class, () -> seller.declineAllForListing(charlie, foreign));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(pending.getId()).getStatus());
        assertEquals(aliceBefore, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size());
        assertTrue(saved.notifications().forUser("charlie", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void aFailureMidwayRollsBackDeclineStatusesAndNotices() {
        Listing charger = liveSale("Charger");
        DealRequest alicePending = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        DealRequest bobPending = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        int aliceBefore = data.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size();

        data.deals().lifecycle().setListener(l -> {
            throw new RuntimeException("forced failure during decline");
        });
        assertThrows(RuntimeException.class, () -> seller.declineAllForListing(charlie, charger));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(alicePending.getId()).getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(bobPending.getId()).getStatus());
        assertEquals(aliceBefore, saved.notifications().forUser("alice", NotificationCategory.OFFER_UPDATE).size());
    }
}
