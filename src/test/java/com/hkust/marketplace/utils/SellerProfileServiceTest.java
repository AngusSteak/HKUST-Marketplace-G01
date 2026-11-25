package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A seller's public profile (brief 1.10): rating, visible reviews and live listings. */
class SellerProfileServiceTest {

    private final DummyDataService data = seeded();
    private final SellerProfileService profiles = new SellerProfileService(data);

    @Test
    void showsTheRatingVisibleReviewsAndLiveListings() {
        Review older = data.reviews().all().get(0);   // seeded 5-star review of charlie
        Review newer = review("charlie", 3, "OK");
        Review hidden = review("charlie", 1, "Rude");
        hidden.setHidden(true);
        data.reviews().save(hidden);

        SellerProfileService.SellerProfile charlie = profiles.profile("CHARLIE");

        assertEquals("charlie", charlie.seller().getUsername());
        assertEquals(List.of(newer, older), charlie.reviews(), "newest first, hidden left out");
        assertEquals(4.0, charlie.averageRating().getAsDouble(), 0.001, "the same reviews the average uses");
        // L100 is reserved by alice's accepted offer, so only the live charger is listed
        assertEquals(List.of("L101"), charlie.liveListings().stream().map(Listing::getId).toList());
    }

    @Test
    void aSellerWithNothingYetHasAnEmptyProfile() {
        SellerProfileService.SellerProfile diana = profiles.profile("diana");

        assertTrue(diana.reviews().isEmpty());
        assertTrue(diana.averageRating().isEmpty());
    }

    @Test
    void disabledSellersShowNoListingsAndDeletedOnesAreMarked() {
        User admin = data.users().find("admin");
        AdminService adminService = new AdminService(data);
        adminService.setActive(admin, data.users().find("charlie"), false);   // the real admin path
        assertTrue(profiles.profile("charlie").liveListings().isEmpty(), "their listings are hidden");

        adminService.setActive(admin, data.users().find("charlie"), true);
        adminService.softDeleteAccount(admin, data.users().find("charlie"));
        SellerProfileService.SellerProfile deleted = profiles.profile("charlie");
        assertTrue(deleted.deleted());
        assertTrue(deleted.liveListings().isEmpty());
        assertEquals(1, deleted.reviews().size(), "past reviews are kept");
    }

    @Test
    void onlySellersHaveProfiles() {
        assertThrows(IllegalArgumentException.class, () -> profiles.profile("alice"));
        assertThrows(IllegalArgumentException.class, () -> profiles.profile("nobody"));
    }

    private Review review(String seller, int rating, String comment) {
        Review review = new Review();
        review.setSellerUsername(seller);
        review.setBuyerUsername("bob");
        review.setRating(rating);
        review.setComment(comment);
        data.reviews().add(review);
        return review;
    }

    private static DummyDataService seeded() {
        DummyDataService service = new DummyDataService();
        service.seedData();
        return service;
    }
}
