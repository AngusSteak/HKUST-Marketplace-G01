package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealLifecycleTest {

    private final DealLifecycle lifecycle = new DealLifecycle();

    // ---------- Submit / counter / accept / decline ----------

    @Test
    void submitCreatesAPendingRequestForTheListingsDealType() {
        Listing listing = liveListing("L1", ListingType.SELL);

        DealLifecycle.Request request = lifecycle.submit(listing);

        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(DealType.SOLD, request.getType());
        assertEquals(List.of(request), lifecycle.requestsFor(listing));
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void submitOnGiveawayListingsIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> lifecycle.submit(liveListing("L1", ListingType.GIVEAWAY)));
    }

    @Test
    void submitOnANonLiveListingIsRejected() {
        Listing listing = liveListing("L1", ListingType.SELL);
        listing.setStatus(ListingStatus.PENDING_REVIEW);

        assertThrows(IllegalStateException.class, () -> lifecycle.submit(listing));
    }

    @Test
    void counterMovesPendingToCountered() {
        DealLifecycle.Request request = lifecycle.submit(liveListing("L1", ListingType.SELL));

        lifecycle.counter(request);

        assertEquals(RequestStatus.COUNTERED, request.getStatus());
    }

    @Test
    void counterCanBeRepeatedWhileCountered() {
        DealLifecycle.Request request = lifecycle.submit(liveListing("L1", ListingType.SELL));

        lifecycle.counter(request);
        lifecycle.counter(request);

        assertEquals(RequestStatus.COUNTERED, request.getStatus());
    }

    @Test
    void acceptingASaleReservesTheListingAndOpensAnActiveDeal() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request request = lifecycle.submit(listing);

        DealLifecycle.DealRecord deal = lifecycle.accept(request);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals(DealType.SOLD, deal.getRequest().getType());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertSame(deal, lifecycle.activeDeal(listing).orElseThrow());
    }

    @Test
    void acceptingABorrowMarksTheListingLent() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.Request request = lifecycle.submit(listing);

        DealLifecycle.DealRecord deal = lifecycle.accept(request);

        assertEquals(DealType.LENT, deal.getRequest().getType());
        assertEquals(ListingStatus.LENT, listing.getStatus());
    }

    @Test
    void acceptingOneRequestDeclinesTheOtherPendingRequests() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request accepted = lifecycle.submit(listing);
        DealLifecycle.Request declined = lifecycle.submit(listing);

        lifecycle.accept(accepted);

        assertEquals(RequestStatus.ACCEPTED, accepted.getStatus());
        assertEquals(RequestStatus.DECLINED, declined.getStatus());
    }

    @Test
    void acceptingOneRequestAlsoDeclinesOtherCounteredRequests() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request accepted = lifecycle.submit(listing);
        DealLifecycle.Request countered = lifecycle.submit(listing);
        lifecycle.counter(countered);

        lifecycle.accept(accepted);

        assertEquals(RequestStatus.DECLINED, countered.getStatus());
    }

    @Test
    void aSecondAcceptanceOnTheSameListingIsRejected() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request first = lifecycle.submit(listing);
        DealLifecycle.Request second = lifecycle.submit(listing);
        lifecycle.accept(first);

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(second));
        assertEquals(RequestStatus.DECLINED, second.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isPresent());
    }

    @Test
    void acceptCounterAcceptsACounteredRequest() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request request = lifecycle.submit(listing);
        lifecycle.counter(request);

        DealLifecycle.DealRecord deal = lifecycle.acceptCounter(request);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }

    @Test
    void declineWorksFromPendingAndCountered() {
        DealLifecycle.Request pending = lifecycle.submit(liveListing("L1", ListingType.SELL));
        DealLifecycle.Request countered = lifecycle.submit(liveListing("L2", ListingType.SELL));
        lifecycle.counter(countered);

        lifecycle.decline(pending);
        lifecycle.decline(countered);

        assertEquals(RequestStatus.DECLINED, pending.getStatus());
        assertEquals(RequestStatus.DECLINED, countered.getStatus());
    }

    // ---------- Giveaway claim ----------

    @Test
    void firstGiveawayClaimWinsAndReservesTheListing() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);

        DealLifecycle.Request claim = lifecycle.claim(listing);

        assertEquals(RequestStatus.ACCEPTED, claim.getStatus());
        assertEquals(DealType.GIVEN, claim.getType());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertEquals(DealStatus.ACTIVE, lifecycle.activeDeal(listing).orElseThrow().getStatus());
    }

    @Test
    void secondGiveawayClaimThrows() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        lifecycle.claim(listing);

        assertThrows(IllegalStateException.class, () -> lifecycle.claim(listing));
    }

    @Test
    void claimingANonGiveawayListingThrows() {
        assertThrows(IllegalStateException.class,
                () -> lifecycle.claim(liveListing("L1", ListingType.SELL)));
    }

    // ---------- Return / complete / cancel ----------

    @Test
    void returningABorrowedItemReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));

        lifecycle.returnItem(deal);

        assertEquals(DealStatus.RETURNED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void autoReturnReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));

        lifecycle.autoReturn(deal);

        assertEquals(DealStatus.AUTO_RETURNED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingASaleMarksTheListingSold() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void completingAGiveawayMarksTheListingGiven() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        DealLifecycle.Request claim = lifecycle.claim(listing);
        DealLifecycle.DealRecord deal = lifecycle.activeDeal(listing).orElseThrow();
        assertSame(claim, deal.getRequest());

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.GIVEN, listing.getStatus());
    }

    @Test
    void completingALoanAfterReturnFinalisesIt() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));
        lifecycle.returnItem(deal);

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingALoanAfterAutoReturnFinalisesIt() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));
        lifecycle.autoReturn(deal);

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingAStaleReturnedLoanLeavesTheRelentDealActive() {
        Listing listing = liveListing("L1", ListingType.LEND);
        DealLifecycle.DealRecord first = lifecycle.accept(lifecycle.submit(listing));
        lifecycle.returnItem(first);
        DealLifecycle.DealRecord second = lifecycle.accept(lifecycle.submit(listing));

        lifecycle.complete(first);

        assertEquals(DealStatus.COMPLETED, first.getStatus());
        assertEquals(DealStatus.ACTIVE, second.getStatus());
        assertEquals(ListingStatus.LENT, listing.getStatus());
        assertSame(second, lifecycle.activeDeal(listing).orElseThrow());
    }

    @Test
    void acceptingARequestForANonLiveListingThrows() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.Request request = lifecycle.submit(listing);
        listing.setStatus(ListingStatus.SOLD);

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(request));
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void cancellingAnActiveDealReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.SELL);
        DealLifecycle.DealRecord deal = lifecycle.accept(lifecycle.submit(listing));

        lifecycle.cancel(deal);

        assertEquals(DealStatus.CANCELLED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    // ---------- Illegal transitions ----------

    @Test
    void pendingRequestCannotAcceptACounter() {
        DealLifecycle.Request pending = lifecycle.submit(liveListing("L1", ListingType.SELL));

        assertThrows(IllegalStateException.class, () -> lifecycle.acceptCounter(pending));
    }

    @Test
    void counteredRequestCannotBeAcceptedDirectly() {
        DealLifecycle.Request countered = lifecycle.submit(liveListing("L1", ListingType.SELL));
        lifecycle.counter(countered);

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(countered));
    }

    @Test
    void acceptedRequestCannotBeCounteredOrDeclined() {
        DealLifecycle.Request accepted =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.SELL))).getRequest();

        assertThrows(IllegalStateException.class, () -> lifecycle.counter(accepted));
        assertThrows(IllegalStateException.class, () -> lifecycle.decline(accepted));
    }

    @Test
    void declinedRequestCannotMoveAgain() {
        DealLifecycle.Request declined = lifecycle.submit(liveListing("L1", ListingType.SELL));
        lifecycle.decline(declined);

        assertThrows(IllegalStateException.class, () -> lifecycle.counter(declined));
        assertThrows(IllegalStateException.class, () -> lifecycle.decline(declined));
        assertThrows(IllegalStateException.class, () -> lifecycle.accept(declined));
        assertThrows(IllegalStateException.class, () -> lifecycle.acceptCounter(declined));
    }

    @Test
    void activeSaleCannotBeReturned() {
        DealLifecycle.DealRecord sale =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.SELL)));

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
    }

    @Test
    void activeLoanCannotBeCompletedBeforeReturn() {
        DealLifecycle.DealRecord loan =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.LEND)));

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(loan));
    }

    @Test
    void returnedDealCannotBeReturnedOrCancelledAgain() {
        DealLifecycle.DealRecord loan =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.LEND)));
        lifecycle.returnItem(loan);

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(loan));
    }

    @Test
    void autoReturnedDealCannotBeReturnedOrCancelledAgain() {
        DealLifecycle.DealRecord loan =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.LEND)));
        lifecycle.autoReturn(loan);

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(loan));
    }

    @Test
    void completedDealCannotMoveAgain() {
        DealLifecycle.DealRecord sale =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.SELL)));
        lifecycle.complete(sale);

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
        assertFalse(lifecycle.activeDeal(sale.getRequest().getListing()).isPresent());
    }

    @Test
    void cancelledDealCannotMoveAgain() {
        DealLifecycle.DealRecord sale =
                lifecycle.accept(lifecycle.submit(liveListing("L1", ListingType.SELL)));
        lifecycle.cancel(sale);

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
    }

    // ---------- Helpers ----------

    private static Listing liveListing(String id, ListingType type) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setType(type);
        listing.setStatus(ListingStatus.LIVE);
        return listing;
    }
}
