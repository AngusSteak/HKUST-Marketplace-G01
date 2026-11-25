package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.DealType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The stats drill-down names a listing's completed deal, or says there is none. */
class SellerStatsOutcomeTest {

    @Test
    void soldDealShowsSold() {
        assertEquals("Sold", SellerDashboardController.outcomeLabel(DealType.SOLD));
    }

    @Test
    void lentDealShowsLent() {
        assertEquals("Lent", SellerDashboardController.outcomeLabel(DealType.LENT));
    }

    @Test
    void givenDealShowsGiven() {
        assertEquals("Given", SellerDashboardController.outcomeLabel(DealType.GIVEN));
    }

    @Test
    void noCompletedDealShowsDash() {
        assertEquals("\u2014", SellerDashboardController.outcomeLabel(null));
    }
}
