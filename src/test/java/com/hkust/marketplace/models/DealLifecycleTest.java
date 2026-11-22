package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealLifecycleTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 12, 0);
    static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    static final Buyer ALICE = new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER);
    static final Buyer BOB = new Buyer("bob", "Bob Wong", "hash", UserRole.BUYER);
    static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    private final DealLifecycle lifecycle = new DealLifecycle(CLOCK);

    // ---------- Submit / counter / accept / decline ----------

    @Test
    void submitCreatesAPendingRequestForTheListingsDealType() {
        Listing sale = liveListing("L1", ListingType.SELL);
        Listing loan = liveListing("L2", ListingType.LEND);

        PriceOffer offer = offer(sale);
        BorrowRequest borrow = borrow(loan);

        assertEquals(RequestStatus.PENDING, offer.getStatus());
        assertEquals(DealType.SOLD, offer.dealType());
        assertEquals(RequestStatus.PENDING, borrow.getStatus());
        assertEquals(DealType.LENT, borrow.dealType());
        assertEquals(NOW, offer.getSubmittedTime());
        assertEquals(ListingStatus.LIVE, sale.getStatus());
        assertEquals(List.of(offer), lifecycle.requestsFor(sale));
        assertEquals(List.of(offer, borrow), lifecycle.requests());
    }

    @Test
    void requestTypeMustMatchTheListingType() {
        Listing giveaway = liveListing("L1", ListingType.GIVEAWAY);
        Listing sale = liveListing("L2", ListingType.SELL);

        assertThrows(IllegalArgumentException.class, () -> offer(giveaway));
        assertThrows(IllegalArgumentException.class, () -> borrow(sale));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitClaim(ALICE, sale));
        assertTrue(lifecycle.requests().isEmpty());
    }

    @Test
    void submitOnANonLiveListingIsRejected() {
        Listing listing = liveListing("L1", ListingType.SELL);
        listing.setStatus(ListingStatus.PENDING_REVIEW);

        assertThrows(IllegalStateException.class, () -> offer(listing));
        assertTrue(lifecycle.requests().isEmpty());
    }

    @Test
    void counterOfferStoresThePriceAndMovesPendingToCountered() {
        PriceOffer offer = offer(liveListing("L1", ListingType.SELL));

        lifecycle.counterOffer(offer, new BigDecimal("120"));

        assertEquals(RequestStatus.COUNTERED, offer.getStatus());
        assertEquals(new BigDecimal("120.00"), offer.getCounterPrice());
        assertEquals(new BigDecimal("120.00"), offer.agreedPrice());
    }

    @Test
    void counterCanBeRepeatedWhileCountered() {
        PriceOffer offer = offer(liveListing("L1", ListingType.SELL));
        lifecycle.counterOffer(offer, new BigDecimal("120"));

        lifecycle.counterOffer(offer, new BigDecimal("110"));

        assertEquals(RequestStatus.COUNTERED, offer.getStatus());
        assertEquals(new BigDecimal("110.00"), offer.getCounterPrice());
    }

    @Test
    void counterBorrowStoresThePeriod() {
        BorrowRequest request = borrow(liveListing("L1", ListingType.LEND));
        LocalDateTime start = NOW.plusHours(2);
        LocalDateTime end = NOW.plusHours(5);

        lifecycle.counterBorrow(request, start, end);

        assertEquals(RequestStatus.COUNTERED, request.getStatus());
        assertEquals(start, request.getCounterStart());
        assertEquals(end, request.agreedEnd());
    }

    @Test
    void acceptingASaleReservesTheListingAndOpensAnActiveDeal() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer offer = offer(listing);

        Deal deal = lifecycle.accept(offer);

        assertEquals(RequestStatus.ACCEPTED, offer.getStatus());
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertSame(offer, deal.getRequest());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertSame(deal, lifecycle.activeDeal(listing).orElseThrow());
        assertEquals(List.of(deal), lifecycle.deals());
    }

    @Test
    void acceptingABorrowMarksTheListingLent() {
        Listing listing = liveListing("L1", ListingType.LEND);

        Deal deal = lifecycle.accept(borrow(listing));

        assertEquals(ListingStatus.LENT, listing.getStatus());
        assertEquals(DealType.LENT, deal.getDealType());
    }

    @Test
    void acceptingOneRequestDeclinesTheOtherPendingRequests() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer accepted = offer(listing);
        PriceOffer declined = lifecycle.submitOffer(BOB, listing, new BigDecimal("90"));

        lifecycle.accept(accepted);

        assertEquals(RequestStatus.ACCEPTED, accepted.getStatus());
        assertEquals(RequestStatus.DECLINED, declined.getStatus());
    }

    @Test
    void acceptingOneRequestAlsoDeclinesOtherCounteredRequests() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer accepted = offer(listing);
        PriceOffer countered = lifecycle.submitOffer(BOB, listing, new BigDecimal("90"));
        lifecycle.counterOffer(countered, new BigDecimal("95"));

        lifecycle.accept(accepted);

        assertEquals(RequestStatus.DECLINED, countered.getStatus());
    }

    @Test
    void aSecondAcceptanceOnTheSameListingIsRejected() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer first = offer(listing);
        PriceOffer second = lifecycle.submitOffer(BOB, listing, new BigDecimal("90"));
        lifecycle.accept(first);

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(second));
    }

    @Test
    void acceptCounterAcceptsACounteredRequest() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer offer = offer(listing);
        lifecycle.counterOffer(offer, new BigDecimal("120"));

        Deal deal = lifecycle.acceptCounter(offer);

        assertEquals(RequestStatus.ACCEPTED, offer.getStatus());
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }

    @Test
    void declineWorksFromPendingAndCountered() {
        PriceOffer pending = offer(liveListing("L1", ListingType.SELL));
        PriceOffer countered = offer(liveListing("L2", ListingType.SELL));
        lifecycle.counterOffer(countered, new BigDecimal("120"));

        lifecycle.decline(pending);
        lifecycle.decline(countered);

        assertEquals(RequestStatus.DECLINED, pending.getStatus());
        assertEquals(RequestStatus.DECLINED, countered.getStatus());
    }

    // ---------- Giveaway claim ----------

    @Test
    void claimIsPendingAndReservesTheListing() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);

        Claim claim = lifecycle.submitClaim(ALICE, listing);

        assertEquals(RequestStatus.PENDING, claim.getStatus());
        assertEquals(DealType.GIVEN, claim.dealType());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void secondClaimFailsWhileTheFirstIsPending() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        lifecycle.submitClaim(ALICE, listing);

        assertThrows(IllegalStateException.class, () -> lifecycle.submitClaim(BOB, listing));
        assertEquals(1, lifecycle.requestsFor(listing).size());
    }

    @Test
    void acceptingAClaimOpensAGivenDeal() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        Claim claim = lifecycle.submitClaim(ALICE, listing);

        Deal deal = lifecycle.accept(claim);

        assertEquals(RequestStatus.ACCEPTED, claim.getStatus());
        assertEquals(DealType.GIVEN, deal.getDealType());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertSame(deal, lifecycle.activeDeal(listing).orElseThrow());
    }

    @Test
    void decliningAClaimRelistsTheGiveawaySoAnotherBuyerCanClaim() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        Claim first = lifecycle.submitClaim(ALICE, listing);

        lifecycle.decline(first);

        assertEquals(RequestStatus.DECLINED, first.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        Claim second = lifecycle.submitClaim(BOB, listing);
        assertEquals(RequestStatus.PENDING, second.getStatus());
        assertThrows(IllegalStateException.class, () -> lifecycle.accept(first));
    }

    // ---------- Return / complete / cancel ----------

    @Test
    void returningABorrowedItemReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.LEND);
        Deal deal = lifecycle.accept(borrow(listing));

        lifecycle.returnItem(deal);

        assertEquals(DealStatus.RETURNED, deal.getStatus());
        assertEquals(NOW, deal.getCompletedDate());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void autoReturnReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.LEND);
        Deal deal = lifecycle.accept(borrow(listing));

        lifecycle.autoReturn(deal);

        assertEquals(DealStatus.AUTO_RETURNED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingASaleMarksTheListingSold() {
        Listing listing = liveListing("L1", ListingType.SELL);
        Deal deal = lifecycle.accept(offer(listing));

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(NOW, deal.getCompletedDate());
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void completingAGiveawayMarksTheListingGiven() {
        Listing listing = liveListing("L1", ListingType.GIVEAWAY);
        Deal deal = lifecycle.accept(lifecycle.submitClaim(ALICE, listing));

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.GIVEN, listing.getStatus());
    }

    @Test
    void completingALoanAfterReturnFinalisesIt() {
        Listing listing = liveListing("L1", ListingType.LEND);
        Deal deal = lifecycle.accept(borrow(listing));
        lifecycle.returnItem(deal);

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingALoanAfterAutoReturnFinalisesIt() {
        Listing listing = liveListing("L1", ListingType.LEND);
        Deal deal = lifecycle.accept(borrow(listing));
        lifecycle.autoReturn(deal);

        lifecycle.complete(deal);

        assertEquals(DealStatus.COMPLETED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void completingAStaleReturnedLoanLeavesTheRelentDealActive() {
        Listing listing = liveListing("L1", ListingType.LEND);
        Deal first = lifecycle.accept(borrow(listing));
        lifecycle.returnItem(first);
        Deal second = lifecycle.accept(borrow(listing));

        lifecycle.complete(first);

        assertEquals(DealStatus.COMPLETED, first.getStatus());
        assertEquals(DealStatus.ACTIVE, second.getStatus());
        assertEquals(ListingStatus.LENT, listing.getStatus());
        assertSame(second, lifecycle.activeDeal(listing).orElseThrow());
    }

    @Test
    void acceptingARequestForANonLiveListingThrows() {
        Listing listing = liveListing("L1", ListingType.SELL);
        PriceOffer offer = offer(listing);
        listing.setStatus(ListingStatus.SOLD);

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(offer));
        assertEquals(RequestStatus.PENDING, offer.getStatus());
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    @Test
    void cancellingAnActiveDealReleasesTheListing() {
        Listing listing = liveListing("L1", ListingType.SELL);
        Deal deal = lifecycle.accept(offer(listing));

        lifecycle.cancel(deal);

        assertEquals(DealStatus.CANCELLED, deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertTrue(lifecycle.activeDeal(listing).isEmpty());
    }

    // ---------- Illegal transitions ----------

    @Test
    void pendingRequestCannotAcceptACounter() {
        PriceOffer pending = offer(liveListing("L1", ListingType.SELL));

        assertThrows(IllegalStateException.class, () -> lifecycle.acceptCounter(pending));
    }

    @Test
    void counteredRequestCannotBeAcceptedDirectly() {
        PriceOffer countered = offer(liveListing("L1", ListingType.SELL));
        lifecycle.counterOffer(countered, new BigDecimal("120"));

        assertThrows(IllegalStateException.class, () -> lifecycle.accept(countered));
    }

    @Test
    void acceptedRequestCannotBeCounteredOrDeclined() {
        PriceOffer accepted = (PriceOffer) lifecycle.accept(offer(liveListing("L1", ListingType.SELL))).getRequest();

        assertThrows(IllegalStateException.class, () -> lifecycle.counterOffer(accepted, BigDecimal.TEN));
        assertThrows(IllegalStateException.class, () -> lifecycle.decline(accepted));
    }

    @Test
    void declinedRequestCannotMoveAgain() {
        PriceOffer declined = offer(liveListing("L1", ListingType.SELL));
        lifecycle.decline(declined);

        assertThrows(IllegalStateException.class, () -> lifecycle.counterOffer(declined, BigDecimal.TEN));
        assertThrows(IllegalStateException.class, () -> lifecycle.decline(declined));
        assertThrows(IllegalStateException.class, () -> lifecycle.accept(declined));
        assertThrows(IllegalStateException.class, () -> lifecycle.acceptCounter(declined));
    }

    @Test
    void activeSaleCannotBeReturned() {
        Deal sale = lifecycle.accept(offer(liveListing("L1", ListingType.SELL)));

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
    }

    @Test
    void activeLoanCannotBeCompletedBeforeReturn() {
        Deal loan = lifecycle.accept(borrow(liveListing("L1", ListingType.LEND)));

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(loan));
    }

    @Test
    void returnedDealCannotBeReturnedOrCancelledAgain() {
        Deal loan = lifecycle.accept(borrow(liveListing("L1", ListingType.LEND)));
        lifecycle.returnItem(loan);

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(loan));
    }

    @Test
    void autoReturnedDealCannotBeReturnedOrCancelledAgain() {
        Deal loan = lifecycle.accept(borrow(liveListing("L1", ListingType.LEND)));
        lifecycle.autoReturn(loan);

        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(loan));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(loan));
    }

    @Test
    void completedDealCannotMoveAgain() {
        Deal sale = lifecycle.accept(offer(liveListing("L1", ListingType.SELL)));
        lifecycle.complete(sale);

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
        assertFalse(lifecycle.activeDeal(sale.getListing()).isPresent());
    }

    @Test
    void cancelledDealCannotMoveAgain() {
        Deal sale = lifecycle.accept(offer(liveListing("L1", ListingType.SELL)));
        lifecycle.cancel(sale);

        assertThrows(IllegalStateException.class, () -> lifecycle.complete(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.returnItem(sale));
        assertThrows(IllegalStateException.class, () -> lifecycle.autoReturn(sale));
    }

    // ---------- Helpers ----------

    private PriceOffer offer(Listing listing) {
        return lifecycle.submitOffer(ALICE, listing, new BigDecimal("100"));
    }

    private BorrowRequest borrow(Listing listing) {
        return lifecycle.submitBorrow(ALICE, listing, NOW.plusHours(1), NOW.plusDays(1));
    }

    static Listing liveListing(String id, ListingType type) {
        Listing listing = switch (type) {
            case SELL -> new SaleListing(CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED,
                    Location.LIBRARY, Money.of("100"));
            case LEND -> new LendListing(CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED,
                    Location.LIBRARY, Duration.ofDays(30));
            case GIVEAWAY -> new GiveawayListing(CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED,
                    Location.LIBRARY);
        };
        listing.setId(id);
        listing.setStatus(ListingStatus.LIVE);
        return listing;
    }
}
