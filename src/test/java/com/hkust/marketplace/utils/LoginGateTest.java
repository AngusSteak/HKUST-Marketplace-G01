package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginGateTest {

    private DummyDataService store;

    @BeforeEach
    void seedFreshStore() {
        store = new DummyDataService();
        store.seedData();
    }

    @Test
    void activeSeedUsersPassTheirOwnPortal() {
        assertEquals(LoginGate.Outcome.OK, check("alice", UserRole.BUYER, "password1"));
        assertEquals(LoginGate.Outcome.OK, check("charlie", UserRole.SELLER, "password1"));
        assertEquals(LoginGate.Outcome.OK, check("admin", UserRole.ADMIN, "admin123"));
    }

    @Test
    void deactivatedSeedUsersAreRefusedOnTheirOwnPortal() {
        for (String username : new String[]{"alice", "bob", "charlie", "diana", "admin"}) {
            store.findUser(username).setActive(false);
        }

        assertEquals(LoginGate.Outcome.DISABLED, check("alice", UserRole.BUYER, "password1"));
        assertEquals(LoginGate.Outcome.DISABLED, check("bob", UserRole.BUYER, "password1"));
        assertEquals(LoginGate.Outcome.DISABLED, check("charlie", UserRole.SELLER, "password1"));
        assertEquals(LoginGate.Outcome.DISABLED, check("diana", UserRole.SELLER, "password1"));
        assertEquals(LoginGate.Outcome.DISABLED, check("admin", UserRole.ADMIN, "admin123"));
    }

    @Test
    void reactivatingAnAccountRestoresLogin() {
        User alice = store.findUser("alice");

        alice.setActive(false);
        assertEquals(LoginGate.Outcome.DISABLED, check("alice", UserRole.BUYER, "password1"));

        alice.setActive(true);
        assertEquals(LoginGate.Outcome.OK, check("alice", UserRole.BUYER, "password1"));
    }

    @Test
    void wrongPortalCredentialsAreRefused() {
        assertEquals(LoginGate.Outcome.WRONG_PORTAL, check("alice", UserRole.SELLER, "password1"));
        assertEquals(LoginGate.Outcome.WRONG_PORTAL, check("admin", UserRole.BUYER, "admin123"));
    }

    @Test
    void wrongPasswordIsRefused() {
        assertEquals(LoginGate.Outcome.WRONG_PASSWORD, check("alice", UserRole.BUYER, "not-the-password"));
    }

    @Test
    void nullUserIsReportedAsNotFoundWithoutNpe() {
        assertEquals(LoginGate.Outcome.USER_NOT_FOUND, LoginGate.check(null, UserRole.BUYER, "password1"));
    }

    @Test
    void passwordIsCheckedBeforeDisabledFlag() {
        store.findUser("alice").setActive(false);

        assertEquals(LoginGate.Outcome.WRONG_PASSWORD, check("alice", UserRole.BUYER, "not-the-password"));
    }

    @Test
    void deactivatedUsersRemainQueryableInTheStore() {
        User alice = store.findUser("alice");
        alice.setActive(false);

        assertSame(alice, store.findUser("alice"));
        assertFalse(store.findUser("alice").isActive());
        assertNotNull(store.getAllUsers().stream()
                .filter(u -> u.getUsername().equals("alice"))
                .findFirst()
                .orElse(null));
        assertTrue(store.getAllUsers().size() >= 5);
    }

    private LoginGate.Outcome check(String username, UserRole role, String password) {
        return LoginGate.check(store.findUser(username), role, password);
    }
}
