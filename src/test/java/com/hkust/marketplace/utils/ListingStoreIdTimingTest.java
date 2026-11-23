package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ListingStoreIdTimingTest {

    private final Database db = Database.inMemory();
    private final UserStore users = new UserStore(db);
    private final ListingStore store = new ListingStore(db);
    private Seller charlie;

    @BeforeEach
    void registerSeller() {
        charlie = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
        users.register(charlie);
    }

    private Listing listing(String title) {
        return new GiveawayListing(charlie, title, "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
    }

    @Test
    void reserveIdClaimsADistinctNumberEachTime() {
        String first = store.reserveId();
        String second = store.reserveId();
        assertEquals("L100", first);
        assertEquals("L101", second);
        assertNotEquals(first, second);
    }

    @Test
    void aReservedIdSurvivesAddAndAddDoesNotAdvanceTheCounterAgain() {
        String reserved = store.reserveId();
        Listing listing = listing("Reserved");
        listing.setId(reserved);
        store.add(listing);

        assertEquals("L100", reserved);
        assertSame(listing, store.find("L100"));

        Listing next = listing("Next");
        store.add(next);
        assertEquals("L101", next.getId(),
                "add() must not move the counter for a listing that already has an id");
    }

    @Test
    void addStillAssignsL100WhenNoIdIsPreSet() {
        Listing listing = listing("No preset");
        store.add(listing);
        assertEquals("L100", listing.getId());
    }
}
