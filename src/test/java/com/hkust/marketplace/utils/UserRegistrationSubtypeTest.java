package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserRegistrationSubtypeTest {

    private static final String PASSWORD_HASH = PasswordHasher.hash("Password1");

    @Test
    void registeringABuyerStoresABuyerRetrievableThroughTheUserBaseType() {
        DummyDataService store = new DummyDataService();
        User buyer = new Buyer("alice", "Alice Chan", PASSWORD_HASH, UserRole.BUYER);
        buyer.setResidenceHall("Hall 3");

        store.registerUser(buyer);

        User stored = store.findUser("ALICE");
        assertSame(buyer, stored);
        assertInstanceOf(Buyer.class, stored);
        assertEquals("Hall 3", stored.getResidenceHall());
    }

    @Test
    void registeringASellerStoresASellerRetrievableThroughTheUserBaseType() {
        DummyDataService store = new DummyDataService();
        User seller = new Seller("charlie", "Charlie Lee", PASSWORD_HASH, UserRole.SELLER);
        seller.setBio("Final year CSE student.");
        seller.setPreferredMeetup("Library");

        store.registerUser(seller);

        User stored = store.findUser("CHARLIE");
        assertSame(seller, stored);
        assertInstanceOf(Seller.class, stored);
        assertEquals("Final year CSE student.", stored.getBio());
        assertEquals("Library", stored.getPreferredMeetup());
    }

    @Test
    void duplicateUsernameIsRejectedAcrossRolesAndKeepsTheFirstAccount() {
        DummyDataService store = new DummyDataService();
        User buyer = new Buyer("alice", "Alice Chan", PASSWORD_HASH, UserRole.BUYER);
        store.registerUser(buyer);

        User seller = new Seller("ALICE", "Impostor Seller", PASSWORD_HASH, UserRole.SELLER);
        store.registerUser(seller);

        User stored = store.findUser("alice");
        assertSame(buyer, stored);
        assertInstanceOf(Buyer.class, stored);
        assertEquals(1, store.getAllUsers().size());
    }
}
