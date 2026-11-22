package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LendingRulesTest {

    @Test
    void lendingDurationMustBeAtLeastFiveSeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> LendingRules.requireLendingDuration(Duration.ofSeconds(4)));
        assertEquals(Duration.ofSeconds(5),
                LendingRules.requireLendingDuration(Duration.ofSeconds(5)));
    }

    @Test
    void lendingDurationIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> LendingRules.requireLendingDuration(null));
    }

    @Test
    void borrowPeriodMustBeAtLeastFiveSeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> LendingRules.requireBorrowPeriod(Duration.ofSeconds(4), Duration.ofDays(1)));
        assertEquals(Duration.ofSeconds(5),
                LendingRules.requireBorrowPeriod(Duration.ofSeconds(5), Duration.ofDays(1)));
    }

    @Test
    void borrowPeriodCannotExceedTheLendingDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> LendingRules.requireBorrowPeriod(Duration.ofDays(2), Duration.ofDays(1)));
        assertEquals(Duration.ofDays(1),
                LendingRules.requireBorrowPeriod(Duration.ofDays(1), Duration.ofDays(1)));
    }
}
