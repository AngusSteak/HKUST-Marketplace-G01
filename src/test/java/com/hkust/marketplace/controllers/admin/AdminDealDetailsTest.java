package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DurationText;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The All Deals details dialog shows the listing's price/duration and meet-up (SRS UC-3.5 step 6). */
class AdminDealDetailsTest {

    private static Seller seller() {
        return new Seller("charlie", "Charlie Chan", "hash", UserRole.SELLER);
    }

    @Test
    void saleDealShowsAskingPriceAndMeetup() {
        Listing sale = new SaleListing(seller(), "MacBook charger", "Original",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("180"));
        sale.setStatus(ListingStatus.SOLD);

        String text = AdminDashboardController.listingBasics(sale);

        assertTrue(text.contains("Price: " + sale.getPriceDisplay()), text);
        assertTrue(text.contains("Meet-up: " + Location.LIBRARY.getLabel()), text);
    }

    @Test
    void lendDealShowsLendingDurationAndMeetup() {
        Duration period = Duration.ofHours(3);
        LendListing lend = new LendListing(seller(), "Projector", "Full HD",
                Category.ELECTRONICS, Condition.USED, Location.STUDENT_CENTRE, period);
        lend.setStatus(ListingStatus.LENT);

        String text = AdminDashboardController.listingBasics(lend);

        assertTrue(text.contains("Lending: " + DurationText.format(period)), text);
        assertTrue(text.contains("Meet-up: " + Location.STUDENT_CENTRE.getLabel()), text);
    }
}
