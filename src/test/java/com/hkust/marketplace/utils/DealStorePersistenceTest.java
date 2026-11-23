package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Requests, deals and the listing statuses they set survive a reload from the database. */
class DealStorePersistenceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService seeded = seeded();

    @Test
    void seededRequestsAndDealsReloadWithTheSameStatesAndDates() {
        DummyDataService reloaded = DummyDataService.open(db);

        List<DealRequest> before = seeded.deals().allRequests();
        List<DealRequest> after = reloaded.deals().allRequests();
        assertEquals(4, after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).getId(), after.get(i).getId());
            assertEquals(before.get(i).getClass(), after.get(i).getClass());
            assertEquals(before.get(i).getStatus(), after.get(i).getStatus());
            assertEquals(before.get(i).getSubmittedTime(), after.get(i).getSubmittedTime());
            assertEquals(before.get(i).getBuyerUsername(), after.get(i).getBuyerUsername());
        }
        assertEquals(new BigDecimal("160.00"), ((PriceOffer) after.get(0)).getOfferedPrice());
        BorrowRequest pending = (BorrowRequest) after.get(3);
        assertEquals(((BorrowRequest) before.get(3)).getEndDate(), pending.getEndDate());

        List<Deal> deals = reloaded.deals().allDeals();
        assertEquals(List.of(DealStatus.ACTIVE, DealStatus.COMPLETED),
                deals.stream().map(Deal::getStatus).toList());
        Deal loan = deals.get(1);
        Deal savedLoan = seeded.deals().allDeals().get(1);
        assertEquals(savedLoan.getStartDate(), loan.getStartDate());
        assertEquals(savedLoan.getDueDate(), loan.getDueDate());
        assertEquals(savedLoan.getCompletedDate(), loan.getCompletedDate());

        assertEquals(ListingStatus.RESERVED, reloaded.listings().find("L100").getStatus());
        assertEquals(ListingStatus.RESERVED, reloaded.listings().find("L102").getStatus());
        assertEquals(ListingStatus.LIVE, reloaded.listings().find("L103").getStatus());
        assertSame(reloaded.listings().find("L100"), deals.get(0).getListing(),
                "deals point at the reloaded listing objects");
    }

    @Test
    void reloadedPendingClaimStillHoldsTheGiveaway() {
        DummyDataService reloaded = DummyDataService.open(db);
        DealLifecycle lifecycle = reloaded.deals().lifecycle();
        Buyer bob = (Buyer) reloaded.users().find("bob");

        assertThrows(IllegalStateException.class,
                () -> lifecycle.submitClaim(bob, reloaded.listings().find("L102")));

        Claim alicesClaim = (Claim) reloaded.deals().findRequest("Q2");
        lifecycle.decline(alicesClaim);
        DummyDataService again = DummyDataService.open(db);
        assertEquals(RequestStatus.DECLINED, again.deals().findRequest("Q2").getStatus());
        assertEquals(ListingStatus.LIVE, again.listings().find("L102").getStatus());
        assertNotNull(again.deals().lifecycle().submitClaim(
                (Buyer) again.users().find("bob"), again.listings().find("L102")));
    }

    @Test
    void reloadedActiveDealStillBlocksNewOffersAndCanComplete() {
        DummyDataService reloaded = DummyDataService.open(db);
        DealLifecycle lifecycle = reloaded.deals().lifecycle();

        assertThrows(IllegalStateException.class, () -> lifecycle.submitOffer(
                (Buyer) reloaded.users().find("bob"), reloaded.listings().find("L100"), new BigDecimal("170")));

        lifecycle.complete(reloaded.deals().allDeals().get(0));
        DummyDataService again = DummyDataService.open(db);
        assertEquals(DealStatus.COMPLETED, again.deals().allDeals().get(0).getStatus());
        assertEquals(ListingStatus.SOLD, again.listings().find("L100").getStatus());
    }

    @Test
    void newIdsContinueAfterTheSavedOnes() {
        DummyDataService reloaded = DummyDataService.open(db);
        DealLifecycle lifecycle = reloaded.deals().lifecycle();

        PriceOffer offer = lifecycle.submitOffer((Buyer) reloaded.users().find("bob"),
                reloaded.listings().find("L101"), new BigDecimal("200"));
        Deal deal = lifecycle.accept(offer);

        assertEquals("Q5", offer.getId());
        assertEquals("D3", deal.getId());
        assertEquals(ListingStatus.RESERVED, DummyDataService.open(db).listings().find("L101").getStatus());
    }

    @Test
    void counterTermsAreSaved() {
        LocalDateTime start = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime end = start.plusDays(20);
        BorrowRequest request = (BorrowRequest) seeded.deals().findRequest("Q4");
        seeded.deals().lifecycle().counterBorrow(request, start, end);

        BorrowRequest back = (BorrowRequest) DummyDataService.open(db).deals().findRequest("Q4");

        assertEquals(RequestStatus.COUNTERED, back.getStatus());
        assertEquals(start, back.getCounterStart());
        assertEquals(end, back.getCounterEnd());
        assertEquals(request.getStartDate(), back.getStartDate());
    }

    @Test
    void listingWithRequestsCannotBeDeleted() {
        assertThrows(DatabaseException.class, () -> seeded.listings().remove("L103"));

        assertNotNull(seeded.listings().find("L103"));
        assertNotNull(DummyDataService.open(db).listings().find("L103"));
    }

    @Test
    void favouritesReloadWithTheRestOfTheData() {
        DummyDataService reloaded = DummyDataService.open(db);
        Buyer alice = (Buyer) reloaded.users().find("alice");

        assertEquals(List.of("L101"), reloaded.favourites().favouriteListingIds(alice));
        assertEquals(List.of("charlie"), reloaded.favourites().followedSellers(alice));
        assertInstanceOf(Buyer.class, alice);
    }

    private DummyDataService seeded() {
        DummyDataService data = new DummyDataService(db);
        data.seedData();
        return data;
    }
}
