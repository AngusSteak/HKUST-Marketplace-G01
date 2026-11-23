package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyDataServiceTest {

    @Test
    void freshInstancesStartEmptyAndDoNotShareState() {
        DummyDataService store = new DummyDataService();
        DummyDataService otherStore = new DummyDataService();

        assertTrue(store.users().all().isEmpty());
        assertTrue(store.listings().all().isEmpty());
        assertTrue(store.deals().allDeals().isEmpty());
        assertTrue(store.reviews().all().isEmpty());

        store.users().register(new Buyer("carol", "Carol Cheung", PasswordHasher.hash("Password1"), UserRole.BUYER));

        assertTrue(store.users().usernameExists("carol"));
        assertTrue(otherStore.users().all().isEmpty());
    }

    @Test
    void seededInstanceHasTheDemoDataInEachStore() {
        DummyDataService store = new DummyDataService();
        store.seedData();

        assertEquals(5, store.users().all().size());
        assertEquals(List.of("L100", "L101", "L102", "L103", "L104"),
                store.listings().all().stream().map(l -> l.getId()).toList());
        assertEquals(2, store.listings().live().size()); // L100 and L102 are RESERVED by seeded deals
        assertEquals(1, store.listings().pending().size());
        assertEquals(ListingStatus.PENDING_REVIEW, store.listings().find("L104").getStatus());
        assertEquals("L100", store.deals().allDeals().get(0).getListing().getId());
        assertEquals(ListingStatus.RESERVED, store.listings().find("L100").getStatus());
        assertEquals(ListingStatus.RESERVED, store.listings().find("L102").getStatus());
        assertEquals(ListingStatus.LIVE, store.listings().find("L103").getStatus());
        assertEquals(4, store.deals().allRequests().size());
        assertEquals(2, store.deals().allDeals().size());
        assertEquals(1, store.notifications().forUser("admin").size());
        assertEquals(5.0, store.reviews().averageRating("charlie").getAsDouble());
    }
}
