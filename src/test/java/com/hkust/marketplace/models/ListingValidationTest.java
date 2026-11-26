package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A listing's required fields and its six-photo limit. */
class ListingValidationTest {

    private final Seller seller = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    @Test
    void everyRequiredFieldIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> giveaway(null, "Lamp", Category.OTHERS, Condition.USED, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> giveaway(seller, null, Category.OTHERS, Condition.USED, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> giveaway(seller, "  ", Category.OTHERS, Condition.USED, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> giveaway(seller, "Lamp", null, Condition.USED, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> giveaway(seller, "Lamp", Category.OTHERS, null, Location.LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> giveaway(seller, "Lamp", Category.OTHERS, Condition.USED, null));
    }

    @Test
    void aMissingDescriptionBecomesEmpty() {
        Listing lamp = new GiveawayListing(seller, "Lamp", null, Category.OTHERS, Condition.USED, Location.LIBRARY);
        assertEquals("", lamp.getDescription());
    }

    @Test
    void photoPathsAreCheckedAndCapped() {
        Listing lamp = giveaway(seller, "Lamp", Category.OTHERS, Condition.USED, Location.LIBRARY);

        assertThrows(IllegalArgumentException.class, () -> lamp.setPhotoPaths(Collections.nCopies(7, "p.jpg")));
        lamp.setPhotoPaths(null);
        assertTrue(lamp.getPhotoPaths().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> lamp.addPhoto(null));
        assertThrows(IllegalArgumentException.class, () -> lamp.addPhoto(" "));

        lamp.setPhotoPaths(new ArrayList<>(Collections.nCopies(6, "p.jpg")));
        assertThrows(IllegalStateException.class, () -> lamp.addPhoto("seventh.jpg"));
        assertEquals(6, lamp.getPhotoPaths().size());
        lamp.setPhotoPaths(List.of("only.jpg"));
        assertEquals(List.of("only.jpg"), lamp.getPhotoPaths());
    }

    private static Listing giveaway(Seller seller, String title, Category category, Condition condition, Location location) {
        return new GiveawayListing(seller, title, "desc", category, condition, location);
    }
}
