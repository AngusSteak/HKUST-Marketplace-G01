package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void registeredUserStoresOnlyABcryptHashThatVerifiesTheRightPassword() {
        DummyDataService store = new DummyDataService();
        store.users().register(new Buyer("carol", "Carol Cheung",
                PasswordHasher.hash("Password1"), UserRole.BUYER));

        User stored = store.users().find("carol");
        assertNotNull(stored);
        assertNotEquals("Password1", stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("$2"));
        assertTrue(PasswordHasher.verify("Password1", stored.getPasswordHash()));
        assertFalse(PasswordHasher.verify("Password2", stored.getPasswordHash()));
    }

    @Test
    void seededDemoUsersStoreBcryptHashesForTheirDocumentedPasswords() {
        DummyDataService store = new DummyDataService();
        store.seedData();

        assertSeededCredential(store, "alice", "password1");
        assertSeededCredential(store, "bob", "password1");
        assertSeededCredential(store, "charlie", "password1");
        assertSeededCredential(store, "diana", "password1");
        assertSeededCredential(store, "admin", "admin123");
    }

    private static void assertSeededCredential(DummyDataService store, String username, String password) {
        User user = store.users().find(username);
        assertNotNull(user, "seeded user " + username + " should exist");
        assertNotEquals(password, user.getPasswordHash(), username + " must not store plaintext");
        assertTrue(PasswordHasher.verify(password, user.getPasswordHash()),
                username + " should log in with the documented demo password");
        assertFalse(PasswordHasher.verify(password + "x", user.getPasswordHash()),
                username + " should reject a wrong password");
    }
}
