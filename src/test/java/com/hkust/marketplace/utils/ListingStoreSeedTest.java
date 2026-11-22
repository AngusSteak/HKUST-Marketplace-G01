package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.SaleListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ListingStoreSeedTest {

    @Test
    void seedProducesTheExpectedListingSubtypesAndStatuses() {
        DummyDataService data = new DummyDataService();
        data.seedData();
        ListingStore listings = data.listings();

        assertEquals(List.of("L100", "L101", "L102", "L103", "L104"),
                listings.all().stream().map(Listing::getId).toList());
        assertInstanceOf(SaleListing.class, listings.find("L100"));
        assertInstanceOf(SaleListing.class, listings.find("L101"));
        assertInstanceOf(GiveawayListing.class, listings.find("L102"));
        assertInstanceOf(LendListing.class, listings.find("L103"));
        assertInstanceOf(SaleListing.class, listings.find("L104"));

        assertEquals(3, listings.live().size());
        assertEquals(1, listings.pending().size());
        assertEquals(ListingStatus.RESERVED, listings.find("L100").getStatus());
        assertEquals(ListingStatus.PENDING_REVIEW, listings.find("L104").getStatus());
    }
}
