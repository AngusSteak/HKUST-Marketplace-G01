package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.hkust.marketplace.models.DealLifecycleTest.ALICE;
import static com.hkust.marketplace.models.DealLifecycleTest.CLOCK;
import static com.hkust.marketplace.models.DealLifecycleTest.NOW;
import static com.hkust.marketplace.models.DealLifecycleTest.liveListing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A deal's buyer, seller, listing and type are derived through its request (SRS Appendix A). */
class DealTest {

    private final DealLifecycle lifecycle = new DealLifecycle(CLOCK);

    @Test
    void buyerSellerListingAndTypeComeFromTheRequest() {
        Listing sale = liveListing("L1", ListingType.SELL);

        Deal deal = lifecycle.accept(lifecycle.submitOffer(ALICE, sale, BigDecimal.TEN));

        assertSame(ALICE, deal.getBuyer());
        assertEquals("alice", deal.getBuyerUsername());
        assertEquals("charlie", deal.getSellerUsername());
        assertSame(sale, deal.getListing());
        assertEquals(DealType.SOLD, deal.getDealType());
        assertEquals(NOW, deal.getStartDate());
        assertNull(deal.getDueDate());
        assertNull(deal.getCompletedDate());
    }

    @Test
    void loanDueDateIsTheRequestedEnd() {
        LocalDateTime end = NOW.plusDays(2);
        BorrowRequest request = lifecycle.submitBorrow(ALICE, liveListing("L1", ListingType.LEND), NOW, end);

        Deal deal = lifecycle.accept(request);

        assertEquals(end, deal.getDueDate());
    }

    @Test
    void loanDueDateIsTheCounteredEndWhenTheBuyerAcceptsACounter() {
        BorrowRequest request = lifecycle.submitBorrow(ALICE, liveListing("L1", ListingType.LEND),
                NOW, NOW.plusDays(2));
        LocalDateTime counterEnd = NOW.plusDays(1);
        CounterOffer counter = lifecycle.counterBorrow(request, NOW, counterEnd);

        Deal deal = lifecycle.acceptCounter(counter);

        assertEquals(counterEnd, deal.getDueDate());
    }

    @Test
    void giveawayDealHasNoDueDate() {
        Deal deal = lifecycle.accept(lifecycle.submitClaim(ALICE, liveListing("L1", ListingType.GIVEAWAY)));

        assertEquals(DealType.GIVEN, deal.getDealType());
        assertNull(deal.getDueDate());
    }

    @Test
    void completedDealTakesExactlyOneReview() {
        Deal deal = lifecycle.accept(lifecycle.submitOffer(ALICE, liveListing("L1", ListingType.SELL), BigDecimal.TEN));
        lifecycle.complete(deal);
        assertFalse(deal.isReviewed());

        Review review = new Review();
        deal.setReview(review);

        assertTrue(deal.isReviewed());
        assertSame(review, deal.getReview());
        assertThrows(IllegalStateException.class, () -> deal.setReview(new Review()));
    }

    @Test
    void activeDealCannotBeReviewed() {
        Deal deal = lifecycle.accept(lifecycle.submitOffer(ALICE, liveListing("L1", ListingType.SELL), BigDecimal.TEN));

        assertThrows(IllegalStateException.class, () -> deal.setReview(new Review()));
        assertFalse(deal.isReviewed());
    }
}
