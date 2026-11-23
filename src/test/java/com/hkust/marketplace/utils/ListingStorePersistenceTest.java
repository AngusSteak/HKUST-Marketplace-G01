package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Listings survive a reload from the same database with every field intact. */
class ListingStorePersistenceTest {

    private final Database db = Database.inMemory();
    private final UserStore users = new UserStore(db);
    private final ListingStore store = new ListingStore(db);
    private Seller charlie;

    @BeforeEach
    void registerSeller() {
        charlie = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
        users.register(charlie);
    }

    @Test
    void allThreeListingTypesReloadWithTheirOwnFields() {
        store.add(new SaleListing(charlie, "Textbook", "8th edition", Category.BOOKS_AND_NOTES,
                Condition.LIKE_NEW, Location.LIBRARY, Money.of("180.50")));
        store.add(new LendListing(charlie, "Calculator", "TI-84", Category.ELECTRONICS,
                Condition.USED, Location.STUDENT_CENTRE, Duration.ofDays(90)));
        store.add(new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE,
                Condition.USED, Location.UG_HALL_V));

        ListingStore reloaded = reload();

        SaleListing sale = assertInstanceOf(SaleListing.class, reloaded.find("L100"));
        assertEquals(Money.of("180.50"), sale.getPrice());
        assertEquals(Category.BOOKS_AND_NOTES, sale.getCategory());
        assertEquals(Condition.LIKE_NEW, sale.getCondition());
        assertEquals(Location.LIBRARY, sale.getMeetupLocation());
        assertEquals("8th edition", sale.getDescription());
        LendListing lend = assertInstanceOf(LendListing.class, reloaded.find("L101"));
        assertEquals(Duration.ofDays(90), lend.getLendingDuration());
        assertInstanceOf(GiveawayListing.class, reloaded.find("L102"));
        assertEquals("charlie", sale.getSellerUsername());
        assertSame(sale.getSeller(), lend.getSeller(), "listings share the one reloaded seller account");
    }

    @Test
    void statusReasonsDatesViewsAndPhotosSurviveUpdate() {
        Listing listing = new SaleListing(charlie, "Textbook", "", Category.BOOKS_AND_NOTES,
                Condition.NEW, Location.LIBRARY, Money.of("100"));
        listing.setPhotoPaths(List.of("data/photos/a.jpg", "data/photos/b.jpg"));
        store.add(listing);
        LocalDateTime submitted = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime reviewed = LocalDateTime.of(2026, 11, 2, 15, 30);

        listing.setStatus(ListingStatus.REJECTED);
        listing.setRejectionReason("Blurry photos");
        listing.setTakedownReason("n/a");
        listing.setSubmittedDate(submitted);
        listing.setReviewedAt(reviewed);
        listing.setViewCount(7);
        listing.setTitle("Calculus Textbook");
        listing.setPhotoPaths(List.of("data/photos/c.jpg"));
        store.update(listing);

        Listing back = reload().find(listing.getId());
        assertEquals(ListingStatus.REJECTED, back.getStatus());
        assertEquals("Blurry photos", back.getRejectionReason());
        assertEquals("n/a", back.getTakedownReason());
        assertEquals(submitted, back.getSubmittedDate());
        assertEquals(reviewed, back.getReviewedAt());
        assertEquals(7, back.getViewCount());
        assertEquals("Calculus Textbook", back.getTitle());
        assertEquals(List.of("data/photos/c.jpg"), back.getPhotoPaths());
    }

    @Test
    void newListingsStartPendingWithNoReviewDate() {
        store.add(new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE, Condition.USED, Location.UG_HALL_V));

        Listing back = reload().find("L100");
        assertEquals(ListingStatus.PENDING_REVIEW, back.getStatus());
        assertNull(back.getReviewedAt());
        assertTrue(back.getPhotoPaths().isEmpty());
    }

    @Test
    void removedListingsStayRemovedAfterReload() {
        store.add(new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE, Condition.USED, Location.UG_HALL_V));
        store.remove("L100");

        assertTrue(reload().all().isEmpty());
    }

    @Test
    void idsContinueAfterTheHighestSavedId() {
        for (int i = 0; i < 5; i++) {
            store.add(new GiveawayListing(charlie, "Item " + i, "", Category.OTHERS, Condition.USED, Location.LIBRARY));
        }
        store.remove("L104");

        ListingStore reloaded = reload();
        Listing next = new GiveawayListing(charlie, "Next", "", Category.OTHERS, Condition.USED, Location.LIBRARY);
        reloaded.add(next);

        assertEquals("L104", next.getId());
    }

    @Test
    void updatingAnUnknownListingIsRefused() {
        Listing stray = new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE, Condition.USED, Location.UG_HALL_V);
        stray.setId("L999");

        assertThrows(IllegalArgumentException.class, () -> store.update(stray));
    }

    @Test
    void viewsAreCountedAndSaved() {
        Listing lamp = new GiveawayListing(charlie, "Lamp", "", Category.FURNITURE, Condition.USED, Location.UG_HALL_V);
        store.add(lamp);

        store.recordView(lamp);
        store.recordView(lamp);

        assertEquals(2, lamp.getViewCount());
        assertEquals(2, reload().find(lamp.getId()).getViewCount());
        Listing stray = new GiveawayListing(charlie, "Stray", "", Category.OTHERS, Condition.USED, Location.LIBRARY);
        stray.setId("L999");
        assertThrows(IllegalArgumentException.class, () -> store.recordView(stray));
    }

    @Test
    void findAllKeepsTheOrderAndSkipsMissingIds() {
        for (String title : List.of("A", "B", "C")) {
            store.add(new GiveawayListing(charlie, title, "", Category.OTHERS, Condition.USED, Location.LIBRARY));
        }

        List<Listing> found = store.findAll(List.of("L102", "L999", "L100"));

        assertEquals(List.of("C", "A"), found.stream().map(Listing::getTitle).toList());
    }

    private ListingStore reload() {
        UserStore freshUsers = new UserStore(db);
        freshUsers.load();
        ListingStore fresh = new ListingStore(db);
        fresh.load(freshUsers);
        return fresh;
    }
}
