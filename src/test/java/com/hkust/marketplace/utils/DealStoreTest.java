package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.PriceOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seeded demo deals and the queries behind the buyer's My Deals and Deal History screens. */
class DealStoreTest {

    private final DealStore deals = seeded().deals();

    @Test
    void aliceHasAPendingClaimAndAnAcceptedOffer() {
        List<DealRequest> open = deals.openRequestsByBuyer("alice");
        List<Deal> active = deals.activeDealsForBuyer("alice");

        assertEquals(1, open.size());
        assertInstanceOf(Claim.class, open.get(0));
        assertEquals("L102", open.get(0).getListing().getId());

        assertEquals(1, active.size());
        assertEquals(DealType.SOLD, active.get(0).getDealType());
        assertEquals(new BigDecimal("160.00"), ((PriceOffer) active.get(0).getRequest()).agreedPrice());
        assertTrue(deals.pastDealsForBuyer("alice").isEmpty());
    }

    @Test
    void bobHasACompletedLoanInHistoryAndANewPendingBorrow() {
        List<Deal> past = deals.pastDealsForBuyer("bob");
        List<DealRequest> open = deals.openRequestsByBuyer("bob");

        assertEquals(1, past.size());
        assertEquals(DealType.LENT, past.get(0).getDealType());
        assertEquals(DealStatus.COMPLETED, past.get(0).getStatus());
        assertEquals(1, open.size());
        assertInstanceOf(BorrowRequest.class, open.get(0));
        assertTrue(deals.activeDealsForBuyer("bob").isEmpty());
    }

    @Test
    void sellerQueriesFollowTheListingsSeller() {
        assertEquals(1, deals.dealsForSeller("charlie").size());
        assertEquals(1, deals.dealsForSeller("diana").size());
        assertEquals(3, deals.requestsForSeller("diana").size());
    }

    @Test
    void usernameLookupsIgnoreCase() {
        assertEquals(deals.requestsByBuyer("alice"), deals.requestsByBuyer("ALICE"));
    }

    @Test
    void pastDealsAreNewestFirst() {
        DummyDataService data = seeded();
        DealStore store = data.deals();
        // bob's second request on the calculator: accept and return it so bob has two past loans
        Deal second = store.lifecycle().accept(store.openRequestsByBuyer("bob").get(0));
        store.lifecycle().returnItem(second);

        List<Deal> past = store.pastDealsForBuyer("bob");

        assertEquals(2, past.size());
        assertEquals(second, past.get(0));
        assertTrue(!past.get(0).getCompletedDate().isBefore(past.get(1).getCompletedDate()));
    }

    private static DummyDataService seeded() {
        DummyDataService data = new DummyDataService();
        data.seedData();
        return data;
    }
}
