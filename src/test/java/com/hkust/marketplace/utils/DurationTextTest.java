package com.hkust.marketplace.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationTextTest {

    @Test
    void shortDurationsReadInSeconds() {
        assertEquals("5 seconds", DurationText.format(Duration.ofSeconds(5)));
        assertEquals("1 second", DurationText.format(Duration.ofSeconds(1)));
    }

    @Test
    void wholeUnitsKeepTheirLargestName() {
        assertEquals("1 day", DurationText.format(Duration.ofDays(1)));
        assertEquals("90 days", DurationText.format(Duration.ofDays(90)));
    }

    @Test
    void showsAtMostTwoUnitsLargestFirst() {
        assertEquals("2 hours 30 minutes", DurationText.format(Duration.ofMinutes(150)));
        assertEquals("5 days 12 hours",
                DurationText.format(Duration.ofDays(5).plusHours(12)));
    }

    @Test
    void zeroAndSubSecondDurationsReadAsZeroSeconds() {
        assertEquals("0 seconds", DurationText.format(Duration.ZERO));
        assertEquals("0 seconds", DurationText.format(Duration.ofMillis(500)));
    }
}
