package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The signed-in user and their role checks, which the screens use to pick a portal. */
class SessionManagerTest {

    @AfterEach
    void signOut() {
        SessionManager.logout();
    }

    @Test
    void nobodyIsSignedInAfterLogout() {
        SessionManager.logout();

        assertNull(SessionManager.getCurrentUser());
        assertFalse(SessionManager.isLoggedIn());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    void aBuyerIsOnlyABuyer() {
        Buyer alice = new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER);
        SessionManager.login(alice);

        assertSame(alice, SessionManager.getCurrentUser());
        assertTrue(SessionManager.isLoggedIn());
        assertTrue(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    void aSellerIsOnlyASeller() {
        SessionManager.login(new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER));

        assertFalse(SessionManager.isBuyer());
        assertTrue(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    void anAdminIsOnlyAnAdmin() {
        SessionManager.login(new Admin("admin", "Admin", "hash", UserRole.ADMIN));

        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertTrue(SessionManager.isAdmin());
    }
}
