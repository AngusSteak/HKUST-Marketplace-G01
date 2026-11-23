package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavouriteStoreTest {

    private static final Buyer ALICE = new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER);
    private static final Buyer BOB = new Buyer("bob", "Bob Wong", "hash", UserRole.BUYER);
    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
    private static final Seller DIANA = new Seller("diana", "Diana Ng", "hash", UserRole.SELLER);

    private final Database db = Database.inMemory();
    private final FavouriteStore store = new FavouriteStore(db);

    @BeforeEach
    void saveUsersAndListings() {
        UserStore users = new UserStore(db);
        List.of(ALICE, BOB, CHARLIE, DIANA).forEach(users::register);
        for (String id : List.of("L1", "L2", "L3")) {
            db.update("INSERT INTO listings (id, type, seller_username, title, description, category,"
                            + " condition, meetup_location, status, submitted_date)"
                            + " VALUES (?, 'GIVEAWAY', 'charlie', 'Test item', '', 'ELECTRONICS', 'USED',"
                            + " 'LIBRARY', 'LIVE', '2026-11-01T09:00')", id);
        }
    }

    @Test
    void toggleFavouriteAddsThenRemoves() {
        Listing listing = listing("L1");

        assertTrue(store.toggleFavourite(ALICE, listing));
        assertTrue(store.isFavourite(ALICE, listing));

        assertFalse(store.toggleFavourite(ALICE, listing));
        assertFalse(store.isFavourite(ALICE, listing));
        assertTrue(store.favouriteListingIds(ALICE).isEmpty());
    }

    @Test
    void favouritesKeepInsertionOrderWithoutDuplicates() {
        store.toggleFavourite(ALICE, listing("L2"));
        store.toggleFavourite(ALICE, listing("L1"));
        store.toggleFavourite(ALICE, listing("L3"));
        store.toggleFavourite(ALICE, listing("L1")); // remove
        store.toggleFavourite(ALICE, listing("L1")); // add back at the end

        assertEquals(List.of("L2", "L3", "L1"), store.favouriteListingIds(ALICE));
    }

    @Test
    void toggleFollowAddsThenRemoves() {
        assertTrue(store.toggleFollow(ALICE, CHARLIE));
        assertTrue(store.isFollowing(ALICE, CHARLIE));
        assertEquals(List.of("charlie"), store.followedSellers(ALICE));

        assertFalse(store.toggleFollow(ALICE, CHARLIE));
        assertFalse(store.isFollowing(ALICE, CHARLIE));
    }

    @Test
    void buyersAreIsolatedFromEachOther() {
        store.toggleFavourite(ALICE, listing("L1"));
        store.toggleFollow(ALICE, CHARLIE);

        assertFalse(store.isFavourite(BOB, listing("L1")));
        assertFalse(store.isFollowing(BOB, CHARLIE));
        assertTrue(store.favouriteListingIds(BOB).isEmpty());
        assertTrue(store.followedSellers(BOB).isEmpty());
    }

    @Test
    void followersOfListsEveryBuyerFollowingTheSeller() {
        store.toggleFollow(BOB, CHARLIE);
        store.toggleFollow(ALICE, CHARLIE);
        store.toggleFollow(ALICE, DIANA);

        assertEquals(List.of("alice", "bob"), store.followersOf(CHARLIE));
        assertEquals(List.of("alice"), store.followersOf(DIANA));
    }

    @Test
    void usernamesAreCaseInsensitive() {
        Buyer aliceUpper = new Buyer("ALICE", "Alice Chan", "hash", UserRole.BUYER);
        store.toggleFollow(aliceUpper, CHARLIE);

        assertTrue(store.isFollowing(ALICE, CHARLIE));
    }

    @Test
    void returnedListsCannotBeModified() {
        store.toggleFavourite(ALICE, listing("L1"));

        assertThrows(UnsupportedOperationException.class, () -> store.favouriteListingIds(ALICE).clear());
    }

    @Test
    void favouriteNeedsABuyerAndAListingWithAnId() {
        assertThrows(NullPointerException.class, () -> store.toggleFavourite(null, listing("L1")));
        assertThrows(NullPointerException.class, () -> store.toggleFavourite(ALICE, new GiveawayListing(
                CHARLIE, "Test item", "", Category.ELECTRONICS, Condition.USED, Location.LIBRARY)));
    }

    @Test
    void seededStoreHasAlicesFavouriteAndFollow() {
        DummyDataService data = new DummyDataService();
        data.seedData();

        assertEquals(List.of("L101"), data.favourites().favouriteListingIds(ALICE));
        assertEquals(List.of("charlie"), data.favourites().followedSellers(ALICE));
    }

    @Test
    void favouritesAndFollowsSurviveAReloadInOrder() {
        store.toggleFavourite(ALICE, listing("L2"));
        store.toggleFavourite(ALICE, listing("L1"));
        store.toggleFavourite(ALICE, listing("L3"));
        store.toggleFavourite(ALICE, listing("L1")); // removed again
        store.toggleFavourite(BOB, listing("L1"));
        store.toggleFollow(ALICE, DIANA);
        store.toggleFollow(ALICE, CHARLIE);

        FavouriteStore reloaded = new FavouriteStore(db);
        reloaded.load();

        assertEquals(List.of("L2", "L3"), reloaded.favouriteListingIds(ALICE));
        assertEquals(List.of("L1"), reloaded.favouriteListingIds(BOB));
        assertEquals(List.of("diana", "charlie"), reloaded.followedSellers(ALICE));
        assertTrue(reloaded.toggleFavourite(ALICE, listing("L1")), "toggling continues from the saved state");
    }

    private static Listing listing(String id) {
        Listing listing = new GiveawayListing(CHARLIE, "Test item", "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
        listing.setId(id);
        return listing;
    }
}
