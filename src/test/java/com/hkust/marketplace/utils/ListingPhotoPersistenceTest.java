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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The seller submit flow reserves an id before saving and stores relative photo paths;
 * both must survive a reload from the database.
 */
class ListingPhotoPersistenceTest {

    private final Database db = Database.inMemory();
    private final UserStore users = new UserStore(db);
    private final ListingStore store = new ListingStore(db);
    private Seller charlie;

    @BeforeEach
    void registerSeller() {
        charlie = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
        users.register(charlie);
    }

    private ListingStore reload() {
        UserStore freshUsers = new UserStore(db);
        freshUsers.load();
        ListingStore fresh = new ListingStore(db);
        fresh.load(freshUsers);
        return fresh;
    }

    @Test
    void reservedIdAndRelativePhotoPathsSurviveReload() {
        String reservedId = store.reserveId();
        Listing listing = new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE,
                Condition.USED, Location.LIBRARY);
        listing.setId(reservedId);
        listing.setPhotoPaths(List.of(
                "data/photos/charlie/L100/a.png",
                "data/photos/charlie/L100/b.png"));
        store.add(listing);

        ListingStore reloaded = reload();
        Listing back = reloaded.find(reservedId);

        assertEquals("L100", reservedId);
        assertEquals(List.of("data/photos/charlie/L100/a.png", "data/photos/charlie/L100/b.png"),
                back.getPhotoPaths());
        assertEquals("L101", reloaded.reserveId(),
                "the reserved id must consume its number so the next listing gets a distinct id");
    }
}
