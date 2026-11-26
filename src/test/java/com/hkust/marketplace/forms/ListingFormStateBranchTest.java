package com.hkust.marketplace.forms;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Create/Edit Listing form state (SRS UC-2.3, UC-2.4): photos, prefill, validation and the dirty check. */
class ListingFormStateBranchTest {

    private final Seller seller = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);

    @Test
    void aNullPhotoDraftIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ListingFormState().addChosenPhoto(null));
    }

    @Test
    void storedPhotoPathsAreCappedAndNullClearsThem() {
        ListingFormState state = new ListingFormState();
        assertThrows(IllegalArgumentException.class,
                () -> state.setPhotoPaths(Collections.nCopies(7, "p.jpg")));

        state.setPhotoPaths(List.of("a.jpg"));
        assertEquals(List.of("a.jpg"), state.getPhotoPaths());
        state.setPhotoPaths(null);
        assertTrue(state.getPhotoPaths().isEmpty());
    }

    @Test
    void loadingNothingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ListingFormState().loadFrom(null));
    }

    @Test
    void loadingAGiveawayClearsAnyPriceOrDurationLeftFromBefore() {
        ListingFormState state = new ListingFormState();
        state.setRawPrice("100");
        state.setRawDurationDays("2");

        state.loadFrom(new GiveawayListing(seller, "Lamp", "Free", Category.FURNITURE, Condition.USED,
                Location.LIBRARY));

        assertEquals(ListingType.GIVEAWAY, state.getType());
        assertNull(state.getRawPrice());
        assertNull(state.getRawDurationDays());
        assertTrue(state.validate(seller).isEmpty());
    }

    @Test
    void loadingALendListingSplitsItsDurationIntoTheFourBoxes() {
        Duration duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);
        ListingFormState state = new ListingFormState();

        state.loadFrom(new LendListing(seller, "Calculator", "TI-84", Category.ELECTRONICS, Condition.USED,
                Location.LIBRARY, duration));

        assertEquals("1", state.getRawDurationDays());
        assertEquals("2", state.getRawDurationHours());
        assertEquals("3", state.getRawDurationMinutes());
        assertEquals("4", state.getRawDurationSeconds());
        assertEquals(duration, state.getLendingDuration());
    }

    @Test
    void anEmptyFormListsEveryMissingField() {
        Map<String, String> errors = new ListingFormState().validate(null);

        assertEquals(List.of("type", "title", "description", "category", "condition", "location", "seller"),
                List.copyOf(errors.keySet()));
    }

    @Test
    void lendDurationAcceptsAnySingleBoxAndRefusesBadNumbers() {
        assertEquals(Duration.ofDays(2), lend(s -> s.setRawDurationDays(" 2 ")).getLendingDuration());
        assertEquals(Duration.ofHours(5), lend(s -> s.setRawDurationHours("5")).getLendingDuration());
        assertEquals(Duration.ofMinutes(30), lend(s -> s.setRawDurationMinutes("30")).getLendingDuration());
        assertEquals(Duration.ofSeconds(10), lend(s -> s.setRawDurationSeconds("10")).getLendingDuration());

        assertTrue(lend(s -> { }).validate(seller).containsKey("duration"), "no duration at all");
        assertTrue(lend(s -> s.setRawDurationHours("two")).validate(seller).containsKey("duration"));
        for (Consumer<ListingFormState> negative : List.<Consumer<ListingFormState>>of(
                s -> s.setRawDurationDays("-1"), s -> s.setRawDurationHours("-1"),
                s -> s.setRawDurationMinutes("-1"), s -> s.setRawDurationSeconds("-1"))) {
            assertTrue(lend(negative).validate(seller).containsKey("duration"));
        }
    }

    @Test
    void anInvalidFormCannotBeBuilt() {
        assertThrows(IllegalStateException.class, () -> new ListingFormState().buildListing(seller));
    }

    @Test
    void validFormsBuildTheMatchingListingTypeWithPhotos() {
        ListingFormState sale = filled(ListingType.SELL);
        sale.setRawPrice("120");
        sale.setPhotoPaths(List.of("a.jpg", "b.png"));

        Listing built = sale.buildListing(seller);

        assertTrue(built instanceof SaleListing);
        assertEquals(Money.of("120"), ((SaleListing) built).getPrice());
        assertEquals(List.of("a.jpg", "b.png"), built.getPhotoPaths());
        assertTrue(filled(ListingType.GIVEAWAY).buildListing(seller) instanceof GiveawayListing);
    }

    @Test
    void anyFilledFieldMakesTheFormDirty() {
        assertFalse(new ListingFormState().isDirty());
        List<Consumer<ListingFormState>> edits = List.of(
                s -> s.setType(ListingType.SELL), s -> s.setTitle("T"), s -> s.setDescription("D"),
                s -> s.setRawPrice("1"), s -> s.setRawDurationDays("1"), s -> s.setRawDurationHours("1"),
                s -> s.setRawDurationMinutes("1"), s -> s.setRawDurationSeconds("1"),
                s -> s.setCategory(Category.OTHERS), s -> s.setCondition(Condition.NEW),
                s -> s.setMeetupLocation(Location.LIBRARY),
                s -> s.addChosenPhoto(new PhotoDraft(Path.of("a.jpg"), "a.jpg")),
                s -> s.setPhotoPaths(List.of("a.jpg")));
        for (Consumer<ListingFormState> edit : edits) {
            ListingFormState state = new ListingFormState();
            edit.accept(state);
            assertTrue(state.isDirty());
        }
        ListingFormState blanks = new ListingFormState();
        blanks.setTitle("  ");
        blanks.setRawDurationSeconds("");
        assertFalse(blanks.isDirty(), "blank text does not count as a change");
    }

    private ListingFormState lend(Consumer<ListingFormState> duration) {
        ListingFormState state = filled(ListingType.LEND);
        duration.accept(state);
        return state;
    }

    private static ListingFormState filled(ListingType type) {
        ListingFormState state = new ListingFormState();
        state.setType(type);
        state.setTitle("Item");
        state.setDescription("Described");
        state.setCategory(Category.ELECTRONICS);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.LIBRARY);
        return state;
    }
}
