package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerServiceCreateListingTest {

    private DummyDataService data;
    private SellerService service;
    private Seller charlie;
    private final PhotoStorage photos = (source, seller, id) ->
            "data/photos/" + seller + "/" + id + "/a.png";

    @BeforeEach
    void setUp() {
        data = new DummyDataService();
        data.seedData();
        service = new SellerService(data);
        charlie = (Seller) data.users().find("charlie");
    }

    private ListingFormState validState() {
        ListingFormState state = new ListingFormState();
        state.setType(com.hkust.marketplace.models.ListingType.GIVEAWAY);
        state.setTitle("Test item");
        state.setDescription("A description");
        state.setCategory(Category.ELECTRONICS);
        state.setCondition(Condition.USED);
        state.setMeetupLocation(Location.LIBRARY);
        state.addChosenPhoto(new com.hkust.marketplace.forms.PhotoDraft(
                Path.of("source.png"), "source.png"));
        return state;
    }

    @Test
    void aBuyerCannotCreateAListing() {
        Buyer alice = (Buyer) data.users().find("alice");
        assertThrows(SecurityException.class,
                () -> service.createListing(alice, validState(), photos));
    }

    @Test
    void aDeactivatedSellerCannotCreateAListing() {
        charlie.setActive(false);
        assertThrows(SecurityException.class,
                () -> service.createListing(charlie, validState(), photos));
    }

    @Test
    void anInvalidFormIsRejectedBeforeAnythingIsSaved() {
        ListingFormState state = validState();
        state.setDescription("");
        assertThrows(IllegalArgumentException.class,
                () -> service.createListing(charlie, state, photos));
    }

    @Test
    void anActiveSellerCreatesAPendingListingAndNotifiesEveryAdmin() {
        Listing listing = service.createListing(charlie, validState(), photos);

        assertInstanceOf(com.hkust.marketplace.models.GiveawayListing.class, listing);
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals("L105", listing.getId());
        assertEquals(List.of("data/photos/charlie/L105/a.png"), listing.getPhotoPaths());
        assertInstanceOf(Listing.class, data.listings().find("L105"));

        for (User admin : data.users().byRole(UserRole.ADMIN)) {
            List<Notification> notes = data.notifications().forUser(admin.getUsername());
            assertTrue(notes.stream().anyMatch(n -> n.getRelatedRef() != null
                            && listing.getId().equals(n.getRelatedRef().getId())),
                    "admin " + admin.getUsername() + " should be notified");
        }
    }
}
