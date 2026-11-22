package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserRegistrationSubtypeTest {

    private static final String PASSWORD_HASH = PasswordHasher.hash("Password1");

    @Test
    void registeringABuyerStoresABuyerRetrievableThroughTheUserBaseType() {
        DummyDataService store = new DummyDataService();
        Buyer buyer = new Buyer("alice", "Alice Chan", PASSWORD_HASH, UserRole.BUYER);
        buyer.setResidenceHall("Hall 3");

        store.users().register(buyer);

        Buyer stored = assertInstanceOf(Buyer.class, store.users().find("ALICE"));
        assertSame(buyer, stored);
        assertEquals("Hall 3", stored.getResidenceHall());
    }

    @Test
    void registeringASellerStoresASellerRetrievableThroughTheUserBaseType() {
        DummyDataService store = new DummyDataService();
        Seller seller = new Seller("charlie", "Charlie Lee", PASSWORD_HASH, UserRole.SELLER);
        seller.setBio("Final year CSE student.");
        seller.setPreferredMeetup(EnumSet.of(Location.LIBRARY));

        store.users().register(seller);

        Seller stored = assertInstanceOf(Seller.class, store.users().find("CHARLIE"));
        assertSame(seller, stored);
        assertEquals("Final year CSE student.", stored.getBio());
        assertEquals(EnumSet.of(Location.LIBRARY), stored.getPreferredMeetup());
    }

    @Test
    void duplicateUsernameIsRejectedAcrossRolesAndKeepsTheFirstAccount() {
        DummyDataService store = new DummyDataService();
        User buyer = new Buyer("alice", "Alice Chan", PASSWORD_HASH, UserRole.BUYER);
        store.users().register(buyer);

        User seller = new Seller("ALICE", "Impostor Seller", PASSWORD_HASH, UserRole.SELLER);
        store.users().register(seller);

        User stored = store.users().find("alice");
        assertSame(buyer, stored);
        assertInstanceOf(Buyer.class, stored);
        assertEquals(1, store.users().all().size());
    }
}
