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

    private static LendListing ninetyDayLoan() {
        return new LendListing(CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED,
                Location.LIBRARY, Duration.ofDays(90));
    }

    @Test
    void aDurationShorterThanFiveSecondsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LendListing(CHARLIE, "Test item", "",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Duration.ofSeconds(4)));
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
