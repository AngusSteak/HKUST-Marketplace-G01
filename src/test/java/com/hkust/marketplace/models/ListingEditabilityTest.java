package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListingEditabilityTest {

    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    private static SaleListing sale() {
        return new SaleListing(CHARLIE, "Item", "Description", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, Money.of("180"));
    }

    private static LendListing lend() {
        return new LendListing(CHARLIE, "Item", "Description", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, Duration.ofDays(2));
    }

    @Test
    void aValidPriceAppliesToAnExistingSaleListing() {
        SaleListing listing = sale();
        listing.setPrice(Money.of("200"));
        assertEquals("200.00", listing.getPrice().amount().toPlainString());
        assertEquals("HK$ 200", listing.getPriceDisplay());
    }

    @Test
    void aNullPriceIsRejectedAndLeavesTheOldPriceIntact() {
        SaleListing listing = sale();
        assertThrows(IllegalArgumentException.class, () -> listing.setPrice(null));
        assertEquals("180.00", listing.getPrice().amount().toPlainString());
    }

    @Test
    void aValidDurationAppliesToAnExistingLendListing() {
        LendListing listing = lend();
        listing.setLendingDuration(Duration.ofDays(5));
        assertEquals(Duration.ofDays(5), listing.getLendingDuration());
    }

    @Test
    void aSubFiveSecondDurationIsRejectedAndLeavesTheOldDurationIntact() {
        LendListing listing = lend();
        assertThrows(IllegalArgumentException.class,
                () -> listing.setLendingDuration(Duration.ofSeconds(4)));
        assertEquals(Duration.ofDays(2), listing.getLendingDuration());
    }

    @Test
    void theListingTypeStaysFixedAfterEditingTypeSpecificFields() {
        SaleListing sale = sale();
        sale.setPrice(Money.of("300"));
        assertEquals(ListingType.SELL, sale.getType());

        LendListing lend = lend();
        lend.setLendingDuration(Duration.ofHours(8));
        assertEquals(ListingType.LEND, lend.getType());
    }
}
