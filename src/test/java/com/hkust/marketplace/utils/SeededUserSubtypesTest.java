package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeededUserSubtypesTest {

    private DummyDataService store;

    @BeforeAll
    void seedStore() {
        store = new DummyDataService();
        store.seedData();
    }

    @Test
    void seededUsersHaveTheSubtypeMatchingTheirRole() {
        assertSeededSubtype("admin", Admin.class, UserRole.ADMIN);
        assertSeededSubtype("alice", Buyer.class, UserRole.BUYER);
        assertSeededSubtype("bob", Buyer.class, UserRole.BUYER);
        assertSeededSubtype("charlie", Seller.class, UserRole.SELLER);
        assertSeededSubtype("diana", Seller.class, UserRole.SELLER);
    }

    @Test
    void seededRoleDataIsReadableThroughSubtypeReferences() {
        Buyer alice = assertInstanceOf(Buyer.class, store.findUser("alice"));
        assertEquals("Hall 3", alice.getResidenceHall());

        Buyer bob = assertInstanceOf(Buyer.class, store.findUser("bob"));
        assertEquals("Off-campus", bob.getResidenceHall());

        Seller charlie = assertInstanceOf(Seller.class, store.findUser("charlie"));
        assertEquals("Final year CSE student selling textbooks and gadgets.", charlie.getBio());
        assertEquals("Library", charlie.getPreferredMeetup());

        Seller diana = assertInstanceOf(Seller.class, store.findUser("diana"));
        assertEquals("Selling furniture and kitchen items before graduation.", diana.getBio());
        assertEquals("Hall 5 Lobby", diana.getPreferredMeetup());
    }

    private void assertSeededSubtype(String username, Class<? extends User> expectedType, UserRole expectedRole) {
        User user = store.findUser(username);
        assertNotNull(user, username + " should be seeded");
        assertInstanceOf(expectedType, user, username + " should be a " + expectedType.getSimpleName());
        assertEquals(expectedRole, user.getRole(), username + " role should match its subtype");
    }
}
