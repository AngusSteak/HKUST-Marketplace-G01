package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.PhotoDraft;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PhotoStorageContractTest {

    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    @Test
    void storeReturnsARelativePathThatCanBeAttachedToAListing() {
        PhotoStorage stub = (source, seller, listingId) ->
                "data/photos/" + seller + "/" + listingId + "/" + source.getFileName();
        String stored = stub.store(Path.of("tmp", "photo.png"), "charlie", "L100");

        assertEquals("data/photos/charlie/L100/photo.png", stored);
        assertFalse(Path.of(stored).isAbsolute());

        Listing listing = new GiveawayListing(CHARLIE, "Item", "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
        listing.addPhoto(stored);
        assertEquals(1, listing.getPhotoPaths().size());
        assertEquals(stored, listing.getPhotoPaths().get(0));
    }

    @Test
    void photoDraftCarriesSourceAndDisplayNameWithoutJavaFx() {
        Path source = Path.of("tmp", "photo.png");
        PhotoDraft draft = new PhotoDraft(source, "photo.png");
        assertEquals(source, draft.source());
        assertEquals("photo.png", draft.displayName());
    }
}
