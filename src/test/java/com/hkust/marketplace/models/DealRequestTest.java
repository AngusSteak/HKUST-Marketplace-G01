package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.hkust.marketplace.models.DealLifecycleTest.ALICE;
import static com.hkust.marketplace.models.DealLifecycleTest.CLOCK;
import static com.hkust.marketplace.models.DealLifecycleTest.NOW;
import static com.hkust.marketplace.models.DealLifecycleTest.liveListing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Input validation for offers, borrow requests and claims (SRS UC-1.4 S1–S3, UC-2.5). */
class DealRequestTest {

    private final DealLifecycle lifecycle = new DealLifecycle(CLOCK);

    // ---------- Price offers ----------

    @Test
    void offerKeepsBuyerListingAndTwoDecimalPrice() {
        Listing sale = liveListing("L1", ListingType.SELL);

        PriceOffer offer = lifecycle.submitOffer(ALICE, sale, new BigDecimal("160.5"));

        assertEquals("alice", offer.getBuyerUsername());
        assertEquals("charlie", offer.getSellerUsername());
        assertEquals(new BigDecimal("160.50"), offer.getOfferedPrice());
        assertNull(offer.getCounterPrice());
        assertEquals(new BigDecimal("160.50"), offer.agreedPrice());
    }

    @Test
    void offerMustBePositiveWithAtMostTwoDecimals() {
        Listing sale = liveListing("L1", ListingType.SELL);

        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitOffer(ALICE, sale, null));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitOffer(ALICE, sale, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitOffer(ALICE, sale, new BigDecimal("-5")));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitOffer(ALICE, sale, new BigDecimal("1.234")));
        assertTrue(lifecycle.requests().isEmpty());
    }

    @Test
    void counterPriceMustBePositiveAndLeavesTheOfferUnchangedWhenInvalid() {
        PriceOffer offer = lifecycle.submitOffer(ALICE, liveListing("L1", ListingType.SELL), BigDecimal.TEN);

        assertThrows(IllegalArgumentException.class, () -> lifecycle.counterOffer(offer, BigDecimal.ZERO));
        assertEquals(RequestStatus.PENDING, offer.getStatus());
        assertNull(offer.getCounterPrice());
    }

    @Test
    void offerNeedsABuyer() {
        Listing sale = liveListing("L1", ListingType.SELL);

        assertThrows(NullPointerException.class, () -> lifecycle.submitOffer(null, sale, BigDecimal.TEN));
    }

    // ---------- Borrow requests ----------

    @Test
    void borrowKeepsThePeriodAndAgreedEndIsTheRequestedEnd() {
        LocalDateTime start = NOW.plusMinutes(1);
        LocalDateTime end = NOW.plusDays(3);

        BorrowRequest request = lifecycle.submitBorrow(ALICE, liveListing("L1", ListingType.LEND), start, end);

        assertEquals(start, request.getStartDate());
        assertEquals(end, request.getEndDate());
        assertEquals(start, request.agreedStart());
        assertEquals(end, request.agreedEnd());
    }

    @Test
    void borrowMayStartNowAndLastExactlyFiveSeconds() {
        BorrowRequest request = lifecycle.submitBorrow(ALICE, liveListing("L1", ListingType.LEND),
                NOW, NOW.plusSeconds(5));

        assertEquals(RequestStatus.PENDING, request.getStatus());
    }

    @Test
    void borrowStartCannotBeInThePast() {
        Listing loan = liveListing("L1", ListingType.LEND);

        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW.minusSeconds(1), NOW.plusHours(1)));
    }

    @Test
    void borrowEndMustBeAfterTheStart() {
        Listing loan = liveListing("L1", ListingType.LEND);

        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW.plusHours(2), NOW.plusHours(2)));
        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW.plusHours(2), NOW.plusHours(1)));
    }

    @Test
    void borrowPeriodMustBeAtLeastFiveSeconds() {
        Listing loan = liveListing("L1", ListingType.LEND);

        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW, NOW.plusSeconds(4)));
    }

    @Test
    void borrowPeriodCannotExceedTheListingsLendingDuration() {
        Listing loan = liveListing("L1", ListingType.LEND);
        loan.setLendingDays(7);

        lifecycle.submitBorrow(ALICE, loan, NOW, NOW.plusDays(7));
        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW, NOW.plusDays(7).plusSeconds(1)));
    }

    @Test
    void borrowNeedsBothDates() {
        Listing loan = liveListing("L1", ListingType.LEND);

        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitBorrow(ALICE, loan, null, NOW.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitBorrow(ALICE, loan, NOW, null));
    }

    @Test
    void counterBorrowUsesTheSameValidation() {
        BorrowRequest request = lifecycle.submitBorrow(ALICE, liveListing("L1", ListingType.LEND),
                NOW.plusHours(1), NOW.plusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.counterBorrow(request, NOW.plusHours(1), NOW.plusHours(1).plusSeconds(4)));
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertNull(request.getCounterEnd());
    }

    // ---------- Ids ----------

    @Test
    void rejectedSubmissionsDoNotUseUpRequestIds() {
        Listing sale = liveListing("L1", ListingType.SELL);
        assertThrows(IllegalArgumentException.class, () -> lifecycle.submitOffer(ALICE, sale, BigDecimal.ZERO));

        PriceOffer offer = lifecycle.submitOffer(ALICE, sale, BigDecimal.TEN);

        assertEquals("Q1", offer.getId());
    }
}
