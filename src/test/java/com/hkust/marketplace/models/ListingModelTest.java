package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListingModelTest {

    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    private static GiveawayListing giveaway() {
        return new GiveawayListing(CHARLIE, "Test item", "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
    }

    @Test
    void aNewListingIsPendingReviewedWithNoViewsAndASubmittedDate() {
        Listing listing = giveaway();

        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals(0, listing.getViewCount());
        assertNotNull(listing.getSubmittedDate());
        assertFalse(listing.getSubmittedDate().isAfter(LocalDateTime.now()));
    }

    @Test
    void aSeventhPhotoIsRejectedAndTheListingStillHasSix() {
        Listing listing = giveaway();
        for (int i = 1; i <= 6; i++) {
            listing.addPhoto("photo-" + i + ".png");
        }

        assertThrows(IllegalStateException.class, () -> listing.addPhoto("photo-7.png"));
        assertEquals(6, listing.getPhotoPaths().size());
    }

    @Test
    void sellerAndTitleAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new GiveawayListing(null, "Test item", "",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> new GiveawayListing(CHARLIE, "  ", "",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY));
    }

    @Test
    void sellerNameAccessorsAreDerivedFromTheSeller() {
        Listing listing = giveaway();

        assertEquals("charlie", listing.getSellerUsername());
        assertEquals("Charlie Lee", listing.getSellerFullName());
    }

    @Test
    void typeDisplayDependsOnTheConcreteSubtype() {
        assertEquals("For Sale", new SaleListing(CHARLIE, "Test item", "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, Money.of("100")).getTypeDisplay());
        assertEquals("For Lending", new LendListing(CHARLIE, "Test item", "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, Duration.ofDays(30)).getTypeDisplay());
        assertEquals("Giveaway", giveaway().getTypeDisplay());
    }
}
