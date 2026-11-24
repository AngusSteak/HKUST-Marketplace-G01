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
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SellerFilteredRequestsTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerService seller;
    private User alice;
    private User bob;
    private User charlie;
    private User diana;

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
        seller = new SellerService(data);
        alice = data.users().find("alice");
        bob = data.users().find("bob");
        charlie = data.users().find("charlie");
        diana = data.users().find("diana");
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
    void filteredQueryReturnsOnlyTheListingsOpenRequestsNewestFirst() {
        Listing charger = liveSale("Charger");
        Listing other = liveSale("Other");

        DealRequest older = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        DealRequest newer = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("175"));
        PriceOffer countered = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("180"));
        data.deals().lifecycle().counterOffer(countered, new BigDecimal("185"));
        DealRequest closed = data.deals().lifecycle()
                .submitOffer((Buyer) bob, charger, new BigDecimal("165"));
        data.deals().lifecycle().decline(closed);
        DealRequest elsewhere = data.deals().lifecycle()
                .submitOffer((Buyer) alice, other, new BigDecimal("10"));

        List<DealRequest> filtered = seller.pendingRequestsForListing(charlie, charger);

        assertEquals(List.of(countered.getId(), newer.getId(), older.getId()),
                filtered.stream().map(DealRequest::getId).toList());
        assertFalse(filtered.contains(closed));
        assertFalse(filtered.contains(elsewhere));
        assertThrows(SecurityException.class, () -> seller.pendingRequestsForListing(diana, charger));
    }

    @Test
    void filteredQueryIncludesThePendingClaimOnAReservedGiveaway() {
        Listing giveaway = liveGiveaway("Free lamp");
        Claim claim = data.deals().lifecycle().submitClaim((Buyer) alice, giveaway);
        assertEquals(ListingStatus.RESERVED, giveaway.getStatus());

        List<DealRequest> filtered = seller.pendingRequestsForListing(charlie, giveaway);

        assertEquals(List.of(claim.getId()), filtered.stream().map(DealRequest::getId).toList());
    }

    @Test
    void filteredQueryIsEmptyAfterDeclineAllForThatListing() {
        Listing charger = liveSale("Charger");
        Listing other = liveSale("Other");
        data.deals().lifecycle().submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        data.deals().lifecycle().submitOffer((Buyer) bob, other, new BigDecimal("90"));

        seller.declineAllForListing(charlie, charger);

        assertEquals(List.of(), seller.pendingRequestsForListing(charlie, charger));
        assertEquals(1, seller.pendingRequestsForListing(charlie, other).size());
    }
}
