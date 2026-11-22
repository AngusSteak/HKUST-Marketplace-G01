package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    private static Locale previousLocale;

    @BeforeAll
    static void useStableFormattingLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    void wholeAmountDisplaysWithoutDecimals() {
        assertEquals("HK$ 180", new Money(new BigDecimal("180")).getDisplay());
    }

    @Test
    void fractionalAmountDisplaysWithTwoDecimals() {
        assertEquals("HK$ 12.50", new Money(new BigDecimal("12.50")).getDisplay());
    }

    @Test
    void toStringUsesTheDisplayValue() {
        Money money = Money.of("180");
        assertEquals(money.getDisplay(), money.toString());
    }

    @Test
    void storesAmountAtScaleTwo() {
        assertEquals(0, Money.of("180").amount().compareTo(new BigDecimal("180.00")));
        assertEquals(2, Money.of("180").amount().scale());
        assertEquals("12.50", Money.of("12.5").amount().toPlainString());
        assertEquals("12.50", Money.of(" 12.50 ").amount().toPlainString());
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("12.345"));
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("0.001")));
    }

    @Test
    void rejectsZeroNegativeNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("0"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("-1"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("  "));
        assertThrows(IllegalArgumentException.class, () -> Money.of(null));
        assertThrows(IllegalArgumentException.class, () -> new Money(null));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("abc"));
    }

    @Test
    void amountsAreComparableAndEqualByValue() {
        Money small = Money.of("12.50");
        Money large = Money.of("180");
        assertTrue(small.compareTo(large) < 0);
        assertTrue(large.compareTo(small) > 0);
        assertEquals(0, small.compareTo(Money.of("12.5")));
        assertEquals(Money.of("12.50"), Money.of("12.5"));
    }
}
