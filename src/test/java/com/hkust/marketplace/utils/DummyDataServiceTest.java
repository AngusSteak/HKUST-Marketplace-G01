package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyDataServiceTest {

    @Test
    void freshInstancesStartEmptyAndDoNotShareState() {
        DummyDataService store = new DummyDataService();
        DummyDataService otherStore = new DummyDataService();

        assertTrue(store.getAllUsers().isEmpty());
        assertTrue(store.getAllListings().isEmpty());
        assertTrue(store.getAllDeals().isEmpty());
        assertTrue(store.getAllReviews().isEmpty());

        store.registerUser(new Buyer("carol", "Carol Cheung", PasswordHasher.hash("Password1"), UserRole.BUYER));

        assertTrue(store.usernameExists("carol"));
        assertTrue(otherStore.getAllUsers().isEmpty());
    }
}
