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
        assertTrue(store.deals().all().isEmpty());
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
        assertEquals(4, store.listings().live().size());
        assertEquals(1, store.listings().pending().size());
        assertEquals(ListingStatus.PENDING_REVIEW, store.listings().find("L104").getStatus());
        assertEquals("L100", store.deals().all().get(0).getListingId());
        assertEquals(1, store.notifications().forUser("admin").size());
        assertEquals(5.0, store.reviews().averageRating("charlie").getAsDouble());
    }
}
