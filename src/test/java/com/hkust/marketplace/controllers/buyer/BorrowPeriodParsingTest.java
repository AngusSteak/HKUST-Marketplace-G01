package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BorrowPeriodParsingTest {

    @Test
    void borrowDialogKeepsEnteredSecondsForFiveSecondBorrow() {
        LocalDateTime start = BuyerDashboardController.borrowDateTime(
                LocalDate.of(2026, 3, 1), "14:30:05", "start");
        LocalDateTime end = BuyerDashboardController.borrowDateTime(
                LocalDate.of(2026, 3, 1), "14:30:10", "end");

        assertEquals(LocalDateTime.of(2026, 3, 1, 14, 30, 5), start);
        assertEquals(Duration.ofSeconds(5), Duration.between(start, end));
    }

    @Test
    void borrowDialogStillAcceptsMinutePrecisionAndRejectsInvalidText() {
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 5),
                BuyerDashboardController.borrowDateTime(LocalDate.of(2026, 3, 1), "9:05", "start"));
        assertThrows(IllegalArgumentException.class,
                () -> BuyerDashboardController.borrowDateTime(LocalDate.of(2026, 3, 1), "soon", "end"));
        assertThrows(IllegalArgumentException.class,
                () -> BuyerDashboardController.borrowDateTime(null, "9:05:00", "start"));
    }

    @Test
    void chatOfferAcceptsFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), ChatViewBuilder.parsePeriod("", "", "5"));
    }

    @Test
    void chatOfferStillAcceptsLargerPeriods() {
        assertEquals(Duration.ofDays(2).plusHours(3).plusSeconds(4),
                ChatViewBuilder.parsePeriod("2", "3", "4"));
        assertEquals(Duration.ofHours(2), ChatViewBuilder.parsePeriod("", "2", ""));
    }

    @Test
    void chatOfferRejectsBlankOrInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> ChatViewBuilder.parsePeriod("", "", ""));
        assertThrows(IllegalArgumentException.class, () -> ChatViewBuilder.parsePeriod("", "abc", ""));
        assertThrows(IllegalArgumentException.class, () -> ChatViewBuilder.parsePeriod("-1", "", ""));
    }
}
