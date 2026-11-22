package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.hkust.marketplace.models.DealLifecycleTest.ALICE;
import static com.hkust.marketplace.models.DealLifecycleTest.CLOCK;
import static com.hkust.marketplace.models.DealLifecycleTest.CHARLIE;
import static com.hkust.marketplace.models.DealLifecycleTest.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LendingDurationTest {

    private static LendListing listingWithDuration(Duration duration) {
        return new LendListing(CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED,
                Location.LIBRARY, duration);
    }

    private static LendListing ninetyDayLoan() {
        return listingWithDuration(Duration.ofDays(90));
    }

    @Test
    void aDurationShorterThanFiveSecondsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> listingWithDuration(Duration.ofSeconds(4)));
    }

    @Test
    void aMissingDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> listingWithDuration(null));
    }

    @Test
    void zeroAndNegativeDurationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> listingWithDuration(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> listingWithDuration(Duration.ofSeconds(-5)));
    }

    @Test
    void exactlyFiveSecondsIsAccepted() {
        assertEquals(Duration.ofSeconds(5),
                listingWithDuration(Duration.ofSeconds(5)).getLendingDuration());
    }

    @Test
    void aNinetyDayDurationIsAccepted() {
        assertEquals(Duration.ofDays(90), ninetyDayLoan().getLendingDuration());
    }

    @Test
    void aBorrowLongerThanTheListingsDurationIsRejected() {
        LendListing loan = ninetyDayLoan();
        loan.setId("L103");
        loan.setStatus(ListingStatus.LIVE);
        DealLifecycle lifecycle = new DealLifecycle(CLOCK);

        lifecycle.submitBorrow(ALICE, loan, NOW, NOW.plusDays(90));
        assertThrows(IllegalArgumentException.class,
                () -> lifecycle.submitBorrow(ALICE, loan, NOW, NOW.plusDays(90).plusSeconds(1)));
    }
}
