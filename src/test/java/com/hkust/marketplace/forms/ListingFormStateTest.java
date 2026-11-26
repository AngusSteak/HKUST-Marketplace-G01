package com.hkust.marketplace.forms;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingFormStateTest {

    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    private static ListingFormState validState(ListingType type) {
        ListingFormState state = new ListingFormState();
        state.setType(type);
        state.setTitle("Test item");
        state.setDescription("A description");
        state.setCategory(Category.ELECTRONICS);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.LIBRARY);
        if (type == ListingType.SELL) {
            state.setRawPrice("180.50");
        } else if (type == ListingType.LEND) {
            state.setRawDurationDays("2");
            state.setRawDurationHours("3");
        }
        return state;
    }

    @Test
    void aFullyValidStateHasNoErrorsForEachType() {
        for (ListingType type : ListingType.values()) {
            assertTrue(validState(type).validate(CHARLIE).isEmpty(), "type " + type);
        }
    }

    @Test
    void buildListingCreatesTheActiveSubclassOnly() {
        Listing sale = validState(ListingType.SELL).buildListing(CHARLIE);
        SaleListing saleListing = assertInstanceOf(SaleListing.class, sale);
        assertEquals("180.50", saleListing.getPrice().amount().toPlainString());

        Listing lend = validState(ListingType.LEND).buildListing(CHARLIE);
        LendListing lendListing = assertInstanceOf(LendListing.class, lend);
        assertEquals(Duration.ofDays(2).plusHours(3), lendListing.getLendingDuration());

        Listing giveaway = validState(ListingType.GIVEAWAY).buildListing(CHARLIE);
        assertInstanceOf(GiveawayListing.class, giveaway);
        assertEquals(ListingStatus.PENDING_REVIEW, giveaway.getStatus());
    }

    @Test
    void aStalePriceOnALendStateNeverReachesTheBuiltListing() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawPrice("99999"); // typed while the form was on Sell, then switched away

        Listing listing = state.buildListing(CHARLIE);
        assertInstanceOf(LendListing.class, listing);
        assertEquals(Duration.ofDays(2).plusHours(3), ((LendListing) listing).getLendingDuration());
    }

    @Test
    void eachInvalidCommonFieldProducesItsOwnKeyedError() {
        ListingFormState state = new ListingFormState();
        state.setType(ListingType.GIVEAWAY);

        Map<String, String> errors = state.validate(CHARLIE);
        assertEquals("Item name is required.", errors.get("title"));
        assertEquals("Description is required.", errors.get("description"));
        assertEquals("Category is required.", errors.get("category"));
        assertEquals("Condition is required.", errors.get("condition"));
        assertEquals("Meet-up location is required.", errors.get("location"));
        assertFalse(errors.containsKey("price"));
        assertFalse(errors.containsKey("duration"));
    }

    @Test
    void malformedAndNonPositivePricesAreRejectedWithKeyedErrors() {
        ListingFormState state = validState(ListingType.SELL);

        state.setRawPrice("not-a-number");
        assertTrue(state.validate(CHARLIE).containsKey("price"));

        state.setRawPrice("-5");
        assertTrue(state.validate(CHARLIE).containsKey("price"));

        state.setRawPrice("0");
        assertTrue(state.validate(CHARLIE).containsKey("price"));

        state.setRawPrice("");
        assertTrue(state.validate(CHARLIE).containsKey("price"));
    }

    @Test
    void aZeroDurationIsRejected() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationDays("0");
        state.setRawDurationHours("0");

        Map<String, String> errors = state.validate(CHARLIE);
        assertTrue(errors.containsKey("duration"));
        assertEquals("Lending duration must be at least 5 seconds", errors.get("duration"));
    }

    @Test
    void aBlankDurationIsRejected() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationDays("");
        state.setRawDurationHours("");

        Map<String, String> errors = state.validate(CHARLIE);
        assertEquals("Lending duration is required", errors.get("duration"));
    }

    @Test
    void aSubFiveSecondDurationIsRejectedByLendingRules() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationDays("0");
        state.setRawDurationHours("0");
        state.setRawDurationSeconds("4");

        Map<String, String> errors = state.validate(CHARLIE);
        assertTrue(errors.containsKey("duration"));
        assertEquals("Lending duration must be at least 5 seconds", errors.get("duration"));
    }

    @Test
    void aMinutesOnlyDurationIsAccepted() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationDays("0");
        state.setRawDurationHours("1");
        state.setRawDurationMinutes("30");
        state.setRawDurationSeconds("0");

        assertTrue(state.validate(CHARLIE).isEmpty());
        assertEquals(Duration.ofSeconds(5_400),
                ((LendListing) state.buildListing(CHARLIE)).getLendingDuration());
    }

    @Test
    void aFiveSecondDurationIsAccepted() {
        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationDays("0");
        state.setRawDurationHours("0");
        state.setRawDurationSeconds("5");

        assertTrue(state.validate(CHARLIE).isEmpty());
        assertEquals(Duration.ofSeconds(5),
                ((LendListing) state.buildListing(CHARLIE)).getLendingDuration());
    }

    @Test
    void buildListingThrowsOnAnInvalidState() {
        ListingFormState state = new ListingFormState();
        state.setType(ListingType.SELL);
        assertThrows(IllegalStateException.class, () -> state.buildListing(CHARLIE));
    }

    @Test
    void switchingTypePreservesCommonAndHiddenValues() {
        ListingFormState state = validState(ListingType.SELL);
        state.setRawPrice("250");

        state.setType(ListingType.LEND);
        assertEquals("Test item", state.getTitle());
        assertEquals(Category.ELECTRONICS, state.getCategory());
        assertEquals("250", state.getRawPrice(), "hidden price value must survive a switch");

        state.setType(ListingType.SELL);
        assertEquals("250", state.getRawPrice(), "switching back restores the typed price");
    }

    @Test
    void isDirtyTracksWhetherAnyFieldWasTouched() {
        ListingFormState state = new ListingFormState();
        assertFalse(state.isDirty());

        state.setTitle("x");
        assertTrue(state.isDirty());

        ListingFormState clean = new ListingFormState();
        clean.setTitle("");
        clean.setDescription("");
        assertFalse(clean.isDirty());
    }

    @Test
    void chosenPhotosAreCappedAtSix() {
        ListingFormState state = new ListingFormState();
        for (int i = 0; i < ListingFormState.MAX_PHOTOS; i++) {
            state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("p" + i + ".png"), "p" + i + ".png"));
        }
        assertFalse(state.canAddMorePhotos());
        assertThrows(IllegalStateException.class,
                () -> state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("p7.png"), "p7.png")));

        state.removeChosenPhoto(state.getChosenPhotos().get(0));
        assertTrue(state.canAddMorePhotos());
        assertEquals(5, state.getChosenPhotos().size());
    }

    @Test
    void sixStoredPhotosLeaveNoRoomForAnother() {
        ListingFormState state = new ListingFormState();
        state.setPhotoPaths(List.of(
                "data/photos/charlie/L100/a.png", "data/photos/charlie/L100/b.png",
                "data/photos/charlie/L100/c.png", "data/photos/charlie/L100/d.png",
                "data/photos/charlie/L100/e.png", "data/photos/charlie/L100/f.png"));

        assertFalse(state.canAddMorePhotos());
        assertTrue(state.getChosenPhotos().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("g.png"), "g.png")));
    }

    @Test
    void storedPhotosCountTowardTheCapAlongsideChosenPhotos() {
        ListingFormState state = new ListingFormState();
        state.setPhotoPaths(List.of(
                "data/photos/charlie/L100/a.png", "data/photos/charlie/L100/b.png",
                "data/photos/charlie/L100/c.png", "data/photos/charlie/L100/d.png"));
        assertTrue(state.canAddMorePhotos());

        state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("e.png"), "e.png"));
        assertTrue(state.canAddMorePhotos());
        state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("f.png"), "f.png"));

        assertFalse(state.canAddMorePhotos());
        assertEquals(2, state.getChosenPhotos().size());
        assertThrows(IllegalStateException.class,
                () -> state.addChosenPhoto(new PhotoDraft(java.nio.file.Path.of("g.png"), "g.png")));

        state.removeChosenPhoto(state.getChosenPhotos().get(0));
        assertTrue(state.canAddMorePhotos());
        assertEquals(1, state.getChosenPhotos().size());
    }

    @Test
    void storedPhotoPathsAreAttachedToTheBuiltListing() {
        ListingFormState state = validState(ListingType.GIVEAWAY);
        state.setPhotoPaths(List.of("data/photos/charlie/L100/a.png", "data/photos/charlie/L100/b.png"));

        Listing listing = state.buildListing(CHARLIE);
        assertEquals(List.of("data/photos/charlie/L100/a.png", "data/photos/charlie/L100/b.png"),
                listing.getPhotoPaths());
    }

    @Test
    void loadFromPrefillsASaleListing() {
        SaleListing listing = new SaleListing(CHARLIE, "Desk lamp", "Warm light",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("180.50"));
        listing.setPhotoPaths(List.of(
                "data/photos/charlie/L1/a.png", "data/photos/charlie/L1/b.png"));

        ListingFormState state = new ListingFormState();
        state.loadFrom(listing);

        assertEquals(ListingType.SELL, state.getType());
        assertEquals("Desk lamp", state.getTitle());
        assertEquals("Warm light", state.getDescription());
        assertEquals(Category.ELECTRONICS, state.getCategory());
        assertEquals(Condition.USED, state.getCondition());
        assertEquals(Location.LIBRARY, state.getMeetupLocation());
        assertEquals("180.50", state.getRawPrice());
        assertEquals(listing.getPhotoPaths(), state.getPhotoPaths());
        assertTrue(state.validate(CHARLIE).isEmpty());
    }

    @Test
    void loadFromPrefillsALendListingAndSplitsItsDuration() {
        LendListing listing = new LendListing(CHARLIE, "Projector", "HD projector",
                Category.ELECTRONICS, Condition.LIKE_NEW, Location.ACADEMIC_BUILDING,
                Duration.ofDays(2).plusHours(3).plusSeconds(15));
        listing.setPhotoPaths(List.of("data/photos/charlie/L2/a.png"));

        ListingFormState state = new ListingFormState();
        state.loadFrom(listing);

        assertEquals(ListingType.LEND, state.getType());
        assertEquals("Projector", state.getTitle());
        assertEquals("HD projector", state.getDescription());
        assertEquals(Category.ELECTRONICS, state.getCategory());
        assertEquals(Condition.LIKE_NEW, state.getCondition());
        assertEquals(Location.ACADEMIC_BUILDING, state.getMeetupLocation());
        assertEquals("2", state.getRawDurationDays());
        assertEquals("3", state.getRawDurationHours());
        assertEquals("0", state.getRawDurationMinutes());
        assertEquals("15", state.getRawDurationSeconds());
        assertEquals(listing.getLendingDuration(), state.getLendingDuration());
        assertEquals(listing.getPhotoPaths(), state.getPhotoPaths());
        assertTrue(state.validate(CHARLIE).isEmpty());
    }

    @Test
    void loadFromPrefillsAGiveawayWithoutAPriceOrDuration() {
        GiveawayListing listing = new GiveawayListing(CHARLIE, "Old books", "Free to a good home",
                Category.BOOKS_AND_NOTES, Condition.USED, Location.STUDENT_CENTRE);
        listing.setPhotoPaths(List.of("data/photos/charlie/L3/a.png"));

        ListingFormState state = new ListingFormState();
        state.loadFrom(listing);

        assertEquals(ListingType.GIVEAWAY, state.getType());
        assertEquals("Old books", state.getTitle());
        assertEquals("Free to a good home", state.getDescription());
        assertEquals(Category.BOOKS_AND_NOTES, state.getCategory());
        assertEquals(Condition.USED, state.getCondition());
        assertEquals(Location.STUDENT_CENTRE, state.getMeetupLocation());
        assertNull(state.getRawPrice());
        assertNull(state.getRawDurationDays());
        assertNull(state.getRawDurationHours());
        assertNull(state.getRawDurationMinutes());
        assertNull(state.getRawDurationSeconds());
        assertEquals(listing.getPhotoPaths(), state.getPhotoPaths());
        assertTrue(state.validate(CHARLIE).isEmpty());
    }
}
