package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seller stats are derived on demand from the seller's visible listings, requests and deals. */
class SellerStatsServiceTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerStatsService service;
    private Buyer alice;
    private Buyer bob;
    private User admin;

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
        service = new SellerStatsService(data);
        alice = (Buyer) data.users().find("alice");
        bob = (Buyer) data.users().find("bob");
        admin = data.users().find("admin");
    }

    private Seller registerSeller(String username) {
        Seller seller = new Seller(username, username, "hash", UserRole.SELLER);
        data.users().register(seller);
        return seller;
    }

    private Listing liveSale(Seller seller, String title, int views) {
        Listing listing = new SaleListing(seller, title, "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("100"));
        listing.setStatus(ListingStatus.LIVE);
        listing.setViewCount(views);
        data.listings().add(listing);
        return listing;
    }

    private Listing liveLend(Seller seller, String title) {
        Listing listing = new LendListing(seller, title, "Description",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Duration.ofDays(90));
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    private Listing liveGiveaway(Seller seller, String title) {
        Listing listing = new GiveawayListing(seller, title, "Description",
                Category.FURNITURE, Condition.USED, Location.LIBRARY);
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    private SellerStats.ListingStat rowFor(SellerStats stats, String listingId) {
        return stats.listings().stream()
                .filter(row -> row.listingId().equals(listingId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void emptySellerHasZeroTotalsNoRowsAndAllThreeDealTypeKeys() {
        Seller erin = registerSeller("erin");

        SellerStats stats = service.statsFor(erin);

        assertEquals(0, stats.totals().views());
        assertEquals(0, stats.totals().offers());
        assertEquals(0, stats.totals().completed());
        assertTrue(stats.totals().averageRating().isEmpty());
        assertEquals(0, stats.totals().visibleReviews());
        assertTrue(stats.listings().isEmpty());
        assertEquals(EnumSet.allOf(DealType.class), stats.completedByType().keySet());
        assertEquals(0, stats.completedByType().get(DealType.SOLD));
        assertEquals(0, stats.completedByType().get(DealType.LENT));
        assertEquals(0, stats.completedByType().get(DealType.GIVEN));
    }

    @Test
    void softDeletedListingContributesNoViewsOffersCompletedOrRow() {
        Seller erin = registerSeller("erin");
        Listing hidden = liveSale(erin, "Hidden", 7);
        data.deals().lifecycle().submitOffer(alice, hidden, new BigDecimal("100"));
        hidden.setDeleted(true);
        data.listings().update(hidden);

        SellerStats stats = service.statsFor(erin);

        assertEquals(0, stats.totals().views());
        assertEquals(0, stats.totals().offers());
        assertEquals(0, stats.totals().completed());
        assertTrue(stats.listings().isEmpty());
        assertTrue(stats.listings().stream().noneMatch(row -> row.listingId().equals(hidden.getId())));
    }

    @Test
    void twoVisibleListingsSumViewsAndOffersIntoTwoRows() {
        Seller erin = registerSeller("erin");
        Listing a = liveSale(erin, "A", 3);
        Listing b = liveSale(erin, "B", 5);
        data.deals().lifecycle().submitOffer(alice, a, new BigDecimal("100"));
        data.deals().lifecycle().submitOffer(alice, b, new BigDecimal("100"));
        data.deals().lifecycle().submitOffer(bob, b, new BigDecimal("110"));

        SellerStats stats = service.statsFor(erin);

        assertEquals(8, stats.totals().views());
        assertEquals(3, stats.totals().offers());
        assertEquals(0, stats.totals().completed());
        assertEquals(2, stats.listings().size());

        SellerStats.ListingStat rowA = rowFor(stats, a.getId());
        assertEquals("A", rowA.title());
        assertEquals(ListingStatus.LIVE, rowA.status());
        assertEquals(3, rowA.views());
        assertEquals(1, rowA.offers());
        assertEquals(0, rowA.completed());

        SellerStats.ListingStat rowB = rowFor(stats, b.getId());
        assertEquals("B", rowB.title());
        assertEquals(5, rowB.views());
        assertEquals(2, rowB.offers());
        assertEquals(0, rowB.completed());

        assertThrows(UnsupportedOperationException.class, () -> stats.listings().clear());
        assertThrows(UnsupportedOperationException.class, () -> stats.completedByType().clear());
    }

    @Test
    void declinedRequestStillCountsAsAnOffer() {
        Seller erin = registerSeller("erin");
        Listing listing = liveSale(erin, "Declined offer", 2);
        DealRequest offer = data.deals().lifecycle()
                .submitOffer(alice, listing, new BigDecimal("100"));
        data.deals().lifecycle().decline(offer);

        SellerStats stats = service.statsFor(erin);

        assertEquals(1, stats.totals().offers());
        assertEquals(1, rowFor(stats, listing.getId()).offers());
        assertEquals(2, stats.totals().views());
    }

    @Test
    void completedStatusesCountButCancelledDoesNot() {
        Seller gina = registerSeller("gina");

        Listing sold = liveSale(gina, "Sold", 1);
        Deal sale = data.deals().lifecycle().accept(
                data.deals().lifecycle().submitOffer(alice, sold, new BigDecimal("100")));
        data.deals().lifecycle().complete(sale);

        Listing returned = liveLend(gina, "Returned loan");
        Deal loanReturned = data.deals().lifecycle().accept(
                data.deals().lifecycle().submitBorrow(alice, returned,
                        LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusDays(1)));
        data.deals().lifecycle().returnItem(loanReturned);

        Listing autoReturned = liveLend(gina, "Auto-returned loan");
        Deal loanAuto = data.deals().lifecycle().accept(
                data.deals().lifecycle().submitBorrow(bob, autoReturned,
                        LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusDays(1)));
        data.deals().lifecycle().autoReturn(loanAuto);

        Listing given = liveGiveaway(gina, "Given away");
        Deal giveaway = data.deals().lifecycle().accept(
                data.deals().lifecycle().submitClaim(alice, given));
        data.deals().lifecycle().complete(giveaway);

        Listing cancelled = liveSale(gina, "Cancelled", 4);
        Deal cancelledDeal = data.deals().lifecycle().accept(
                data.deals().lifecycle().submitOffer(bob, cancelled, new BigDecimal("100")));
        data.deals().lifecycle().cancel(cancelledDeal);

        SellerStats stats = service.statsFor(gina);

        assertEquals(4, stats.totals().completed());
        assertEquals(1, stats.completedByType().get(DealType.SOLD));
        assertEquals(2, stats.completedByType().get(DealType.LENT));
        assertEquals(1, stats.completedByType().get(DealType.GIVEN));
        assertEquals(5, stats.totals().offers());

        assertEquals(1, rowFor(stats, sold.getId()).completed());
        assertEquals(1, rowFor(stats, returned.getId()).completed());
        assertEquals(1, rowFor(stats, autoReturned.getId()).completed());
        assertEquals(1, rowFor(stats, given.getId()).completed());
        assertEquals(0, rowFor(stats, cancelled.getId()).completed());
    }

    @Test
    void completedByTypeAlwaysHasExactlyTheThreeDealTypeKeys() {
        Seller charlie = (Seller) data.users().find("charlie");

        SellerStats stats = service.statsFor(charlie);

        assertEquals(EnumSet.allOf(DealType.class), stats.completedByType().keySet());
        assertEquals(3, stats.completedByType().size());
    }

    @Test
    void buyerAdminNullAndInactiveSellerAreRefused() {
        Seller frank = registerSeller("frank");
        frank.setActive(false);
        data.users().save(frank);

        assertThrows(SecurityException.class, () -> service.statsFor(alice));
        assertThrows(SecurityException.class, () -> service.statsFor(admin));
        assertThrows(SecurityException.class, () -> service.statsFor(null));
        assertThrows(SecurityException.class, () -> service.statsFor(frank));
    }
}
