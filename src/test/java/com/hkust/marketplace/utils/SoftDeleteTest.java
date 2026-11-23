package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDeleteTest {

    private DummyDataService store;

    @BeforeEach
    void seedFreshStore() {
        store = new DummyDataService();
        store.seedData();
    }

    private LoginGate.Outcome check(String username, UserRole role, String password) {
        return LoginGate.check(store.users().find(username), role, password);
    }

    @Test
    void deletedAccountsAreRefusedOnTheirOwnPortal() {
        store.users().find("alice").setDeleted(true);
        store.users().find("charlie").setDeleted(true);
        store.users().find("admin").setDeleted(true);

        assertEquals(LoginGate.Outcome.DELETED, check("alice", UserRole.BUYER, "password1"));
        assertEquals(LoginGate.Outcome.DELETED, check("charlie", UserRole.SELLER, "password1"));
        assertEquals(LoginGate.Outcome.DELETED, check("admin", UserRole.ADMIN, "admin123"));
    }

    @Test
    void passwordIsCheckedBeforeDeletedFlag() {
        store.users().find("alice").setDeleted(true);

        assertEquals(LoginGate.Outcome.WRONG_PASSWORD, check("alice", UserRole.BUYER, "wrong"));
    }

    @Test
    void deletedUsersAreAbsentFromListsButStillFindable() {
        User alice = store.users().find("alice");
        alice.setDeleted(true);

        assertFalse(store.users().all().contains(alice));
        assertFalse(store.users().byRole(UserRole.BUYER).contains(alice));
        assertEquals(4, store.users().all().size());

        assertSame(alice, store.users().find("alice"));
        assertTrue(store.users().usernameExists("alice"));
    }

    @Test
    void deletedUsernameStaysReserved() {
        store.users().find("alice").setDeleted(true);

        store.users().register(new Buyer(
                "alice", "Impostor", PasswordHasher.hash("Password1!"), UserRole.BUYER));

        assertEquals("Alice Chan", store.users().find("alice").getFullName());
        assertEquals(4, store.users().all().size());
    }

    @Test
    void historicalRecordsLabelDeletedUsers() {
        User alice = store.users().find("alice");
        User charlie = store.users().find("charlie");
        alice.setDeleted(true);
        charlie.setDeleted(true);

        assertEquals("deleted user", alice.getDisplayName());
        assertEquals("deleted user", charlie.getDisplayName());
        assertEquals("deleted user", store.users().historicalLabel("alice"));
        assertEquals("deleted user", store.listings().find("L100").getSellerFullName());

        Deal deal = store.deals().allDeals().get(0);
        assertEquals("deleted user", deal.getBuyerDisplayName());
        assertEquals("deleted user", deal.getSellerDisplayName());
        assertEquals("deleted user", deal.getRequest().getBuyerDisplayName());
        assertEquals("deleted user", deal.getRequest().getSellerDisplayName());
    }

    @Test
    void activeRecordsKeepTheirNormalLabels() {
        assertEquals("Alice Chan", store.users().find("alice").getDisplayName());
        assertEquals("@alice", store.users().historicalLabel("alice"));
        assertEquals("Charlie Lee", store.listings().find("L100").getSellerFullName());

        Deal deal = store.deals().allDeals().get(0);
        assertEquals("Alice Chan", deal.getBuyerDisplayName());
        assertEquals("Charlie Lee", deal.getSellerDisplayName());
    }

    @Test
    void deletedTakesPriorityOverDisabledFlag() {
        User alice = store.users().find("alice");
        alice.setActive(false);
        alice.setDeleted(true);

        assertEquals(LoginGate.Outcome.DELETED, check("alice", UserRole.BUYER, "password1"));
        assertFalse(alice.isActive());
    }
}
