package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.forms.PhotoDraft;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerEditTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private SellerService seller;
    private User adminUser;
    private User alice;
    private User charlie;
    private User diana;
    private final PhotoStorage photos = (source, sellerUsername, id) ->
            "data/photos/" + sellerUsername + "/" + id + "/new.png";

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
        seller = new SellerService(data);
        adminUser = data.users().find("admin");
        alice = data.users().find("alice");
        charlie = data.users().find("charlie");
        diana = data.users().find("diana");
    }

    private ListingFormState validState(ListingType type) {
        ListingFormState state = new ListingFormState();
        state.setType(type);
        state.setTitle("Edited title");
        state.setDescription("Edited description");
        state.setCategory(Category.BOOKS_AND_NOTES);
        state.setCondition(Condition.LIKE_NEW);
        state.setMeetupLocation(Location.STUDENT_CENTRE);
        if (type == ListingType.SELL) {
            state.setRawPrice("450");
        } else if (type == ListingType.LEND) {
            state.setRawDurationDays("0");
            state.setRawDurationHours("0");
            state.setRawDurationSeconds("5");
        }
        return state;
    }

    @Test
    void rejectedEditReturnsToPendingReviewClearsReasonsAndNotifiesAdminsAndSurvivesReload() {
        Listing fridge = data.listings().find("L104");
        fridge.setStatus(ListingStatus.REJECTED);
        fridge.setRejectionReason("Blurry photo");
        fridge.setTakedownReason("Earlier takedown");
        fridge.setReviewedAt(LocalDateTime.now());
        int noticesBefore = data.notifications().forUser("admin", NotificationCategory.NEW_SUBMISSION).size();

        seller.updateListing(diana, fridge, validState(ListingType.SELL), photos);

        assertEquals(ListingStatus.PENDING_REVIEW, fridge.getStatus());
        assertNull(fridge.getRejectionReason());
        assertNull(fridge.getTakedownReason());
        assertNull(fridge.getReviewedAt());
        assertEquals("450.00", ((SaleListing) fridge).getPrice().amount().toPlainString());

        DummyDataService saved = DummyDataService.open(db);
        Listing back = saved.listings().find("L104");
        assertEquals(ListingStatus.PENDING_REVIEW, back.getStatus());
        assertNull(back.getRejectionReason());
        assertNull(back.getTakedownReason());
        assertNull(back.getReviewedAt());
        assertEquals("Edited title", back.getTitle());
        assertEquals("Edited description", back.getDescription());
        assertEquals(Category.BOOKS_AND_NOTES, back.getCategory());
        assertEquals(Condition.LIKE_NEW, back.getCondition());
        assertEquals(Location.STUDENT_CENTRE, back.getMeetupLocation());
        assertEquals("450.00", ((SaleListing) back).getPrice().amount().toPlainString());

        List<Notification> notices = saved.notifications()
                .forUser("admin", NotificationCategory.NEW_SUBMISSION);
        assertEquals(noticesBefore + 1, notices.size());
        assertTrue(notices.stream().anyMatch(n -> n.getMessage().contains("resubmitted")));
        assertEquals(NotificationRef.Target.LISTING, notices.get(0).getRelatedRef().getTarget());
        assertEquals("L104", notices.get(0).getRelatedRef().getId());
    }

    @Test
    void aPendingOfferRefusesEditingWithNothingChanged() {
        Listing charger = data.listings().find("L101");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        assertEquals(RequestStatus.PENDING, offer.getStatus());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.updateListing(charlie, charger, validState(ListingType.SELL), photos));
        assertEquals(SellerService.HAS_ACTIVE_OFFERS, ex.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        Listing back = saved.listings().find("L101");
        assertEquals("MacBook Pro Charger 61W", back.getTitle());
        assertEquals("250.00", ((SaleListing) back).getPrice().amount().toPlainString());
        assertEquals(ListingStatus.LIVE, back.getStatus());
        assertEquals(RequestStatus.PENDING, saved.deals().findRequest(offer.getId()).getStatus());
    }

    @Test
    void aCounteredOfferAlsoRefusesEditing() {
        Listing charger = data.listings().find("L101");
        PriceOffer offer = data.deals().lifecycle()
                .submitOffer((Buyer) alice, charger, new BigDecimal("170"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("180"));
        assertEquals(RequestStatus.COUNTERED, offer.getStatus());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.updateListing(charlie, charger, validState(ListingType.SELL), photos));
        assertEquals(SellerService.HAS_ACTIVE_OFFERS, ex.getMessage());
        assertEquals("MacBook Pro Charger 61W", DummyDataService.open(db).listings().find("L101").getTitle());
    }

    @Test
    void otherSellerBuyerAndAdminAreRefusedWithNoChanges() {
        Listing fridge = data.listings().find("L104");
        for (User caller : List.of(charlie, alice, adminUser)) {
            assertThrows(SecurityException.class,
                    () -> seller.updateListing(caller, fridge, validState(ListingType.SELL), photos));
        }

        DummyDataService saved = DummyDataService.open(db);
        assertEquals("Mini Fridge", saved.listings().find("L104").getTitle());
        assertEquals(ListingStatus.PENDING_REVIEW, saved.listings().find("L104").getStatus());
    }

    @Test
    void aDeletedListingIsRefused() {
        Listing fridge = data.listings().find("L104");
        fridge.setDeleted(true);
        data.listings().update(fridge);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seller.updateListing(diana, fridge, validState(ListingType.SELL), photos));
        assertEquals(SellerService.NOT_CHANGEABLE, ex.getMessage());

        DummyDataService saved = DummyDataService.open(db);
        assertTrue(saved.listings().find("L104").isDeleted());
        assertEquals("Mini Fridge", saved.listings().find("L104").getTitle());
    }

    @Test
    void anInvalidFormSavesNothing() {
        Listing fridge = data.listings().find("L104");
        ListingFormState state = validState(ListingType.SELL);
        state.setDescription("");

        assertThrows(IllegalArgumentException.class,
                () -> seller.updateListing(diana, fridge, state, photos));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals("Mini Fridge", saved.listings().find("L104").getTitle());
        assertEquals(ListingStatus.PENDING_REVIEW, saved.listings().find("L104").getStatus());
    }

    @Test
    void aTypeMismatchIsRefusedAndSavesNothing() {
        Listing fridge = data.listings().find("L104");

        assertThrows(IllegalArgumentException.class,
                () -> seller.updateListing(diana, fridge, validState(ListingType.LEND), photos));

        DummyDataService saved = DummyDataService.open(db);
        assertEquals("Mini Fridge", saved.listings().find("L104").getTitle());
        assertEquals("400.00", ((SaleListing) saved.listings().find("L104")).getPrice().amount().toPlainString());
    }

    @Test
    void lendingDurationIsAppliedThroughTheValidatedAccessor() {
        Listing bike = new LendListing((com.hkust.marketplace.models.Seller) diana, "Bike", "Desc",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, java.time.Duration.ofDays(3));
        data.listings().add(bike);

        ListingFormState state = validState(ListingType.LEND);
        state.setRawDurationSeconds("5");
        seller.updateListing(diana, bike, state, photos);

        DummyDataService saved = DummyDataService.open(db);
        Listing back = saved.listings().find(bike.getId());
        assertEquals("Edited title", back.getTitle());
        assertEquals(ListingStatus.PENDING_REVIEW, back.getStatus());
    }

    @Test
    void existingPhotoPathsSurviveAnEditAndChosenDraftsAreAppended() {
        Listing fridge = data.listings().find("L104");
        fridge.addPhoto("data/photos/diana/L104/existing.png");
        data.listings().update(fridge);
        ListingFormState state = validState(ListingType.SELL);
        state.addChosenPhoto(new PhotoDraft(Path.of("chosen.png"), "chosen.png"));

        seller.updateListing(diana, fridge, state, photos);

        assertEquals(List.of("data/photos/diana/L104/existing.png",
                        "data/photos/diana/L104/new.png"),
                DummyDataService.open(db).listings().find("L104").getPhotoPaths());
    }
}
