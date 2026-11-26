package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SellerService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SellerDashboardMarkSoldTest {

    private static final Seller SELLER = new Seller("charlie", "Charlie", "hash", UserRole.SELLER);

    private static Listing sale(ListingStatus status) {
        Listing listing = new SaleListing(SELLER, "Desk lamp", "Warm light",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("100"));
        listing.setStatus(status);
        return listing;
    }

    private static Listing lend(ListingStatus status) {
        Listing listing = new LendListing(SELLER, "Projector", "HD projector",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Duration.ofDays(30));
        listing.setStatus(status);
        return listing;
    }

    private static Listing giveaway(ListingStatus status) {
        Listing listing = new GiveawayListing(SELLER, "Old books", "Free to a good home",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY);
        listing.setStatus(status);
        return listing;
    }

    @Test
    void liveSaleCanBeMarkedSold() {
        assertNull(SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.LIVE)));
    }

    @Test
    void lendAndGiveawayListingsReportTheSaleOnlyRule() {
        assertEquals(SellerService.NOT_SALE_LISTING,
                SellerDashboardController.markSoldUnavailableReason(lend(ListingStatus.LIVE)));
        assertEquals(SellerService.NOT_SALE_LISTING,
                SellerDashboardController.markSoldUnavailableReason(giveaway(ListingStatus.LIVE)));
    }

    @Test
    void pendingReviewAndRejectedListingsReportTheLiveOnlyRule() {
        assertEquals(SellerService.NOT_LIVE,
                SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.PENDING_REVIEW)));
        assertEquals(SellerService.NOT_LIVE,
                SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.REJECTED)));
    }

    @Test
    void reservedSoldLentGivenAndTakenDownListingsCanNoLongerBeChanged() {
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.RESERVED)));
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.SOLD)));
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(lend(ListingStatus.LENT)));
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(giveaway(ListingStatus.GIVEN)));
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(sale(ListingStatus.TAKEN_DOWN)));
    }

    @Test
    void deletedLiveSaleCanNoLongerBeChanged() {
        Listing listing = sale(ListingStatus.LIVE);
        listing.setDeleted(true);
        assertEquals(SellerService.NOT_CHANGEABLE,
                SellerDashboardController.markSoldUnavailableReason(listing));
    }
}
