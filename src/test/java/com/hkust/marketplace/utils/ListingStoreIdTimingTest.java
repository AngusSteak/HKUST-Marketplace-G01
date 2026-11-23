package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ListingStoreIdTimingTest {

    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    private static Listing listing(String title) {
        return new GiveawayListing(CHARLIE, title, "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
    }

    @Test
    void peekNextIdDoesNotIncrementTheCounter() {
        ListingStore store = new ListingStore();
        assertEquals("L100", store.peekNextId());
        assertEquals("L100", store.peekNextId());
    }

    @Test
    void aPreSetPeekedIdSurvivesAddAndTheNextNewListingGetsTheFollowingId() {
        ListingStore store = new ListingStore();
        Listing reserved = listing("Reserved");
        reserved.setId(store.peekNextId());
        store.add(reserved);

        assertEquals("L100", reserved.getId());
        assertSame(reserved, store.find("L100"));

        Listing next = listing("Next");
        store.add(next);
        assertEquals("L101", next.getId());
    }

    @Test
    void addStillAssignsL100WhenNoIdIsPreSet() {
        ListingStore store = new ListingStore();
        Listing listing = listing("No preset");
        store.add(listing);
        assertEquals("L100", listing.getId());
    }
}
