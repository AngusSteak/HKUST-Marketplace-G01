package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.ListingType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Admin listing Edit dialog highlights invalid fields and stays open (SRS UC-3.7 A4). */
class AdminEditListingValidationTest {

    @Test
    void validSaleAndLendInputHasNoErrors() {
        assertTrue(AdminDashboardController.adminEditErrors(
                ListingType.SELL, "MacBook charger", "Original", "180.00", "", "", "").isEmpty());
        assertTrue(AdminDashboardController.adminEditErrors(
                ListingType.LEND, "Projector", "Full HD", "", "0", "1", "0").isEmpty());
        assertTrue(AdminDashboardController.adminEditErrors(
                ListingType.GIVEAWAY, "Desk lamp", "Works", "", "", "", "").isEmpty());
    }

    @Test
    void blankTitleAndDescriptionAreReported() {
        Map<String, String> errors = AdminDashboardController.adminEditErrors(
                ListingType.SELL, "  ", "", "180", "", "", "");

        assertEquals("Title is required", errors.get("title"));
        assertEquals("Description is required", errors.get("description"));
    }

    @Test
    void invalidSalePriceIsReported() {
        assertEquals("Asking price is required", AdminDashboardController.adminEditErrors(
                ListingType.SELL, "Chair", "Sturdy", "  ", "", "", "").get("price"));

        assertTrue(AdminDashboardController.adminEditErrors(
                ListingType.SELL, "Chair", "Sturdy", "not a number", "", "", "")
                .get("price").startsWith("Invalid asking price"));
    }

    @Test
    void shortLendDurationIsReported() {
        Map<String, String> errors = AdminDashboardController.adminEditErrors(
                ListingType.LEND, "Projector", "Full HD", "", "0", "0", "4");

        assertEquals("Lending duration must be at least 5 seconds", errors.get("duration"));
    }

    @Test
    void nonNumericLendDurationIsReported() {
        assertEquals("Lending days, hours and seconds must be whole numbers.",
                AdminDashboardController.adminEditErrors(
                        ListingType.LEND, "Projector", "Full HD", "", "1.5", "0", "0")
                        .get("duration"));
    }
}
