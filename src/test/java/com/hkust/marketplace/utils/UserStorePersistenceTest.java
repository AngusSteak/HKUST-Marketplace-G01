package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Accounts survive a reload from the same database with every field intact. */
class UserStorePersistenceTest {

    private final Database db = Database.inMemory();
    private final UserStore store = new UserStore(db);

    @Test
    void everyRoleReloadsWithItsOwnFields() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 1, 10, 0);
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash-a", UserRole.BUYER);
        buyer.setResidenceHall("UG Hall V");
        buyer.setCreatedAt(created);
        Seller seller = new Seller("charlie", "Charlie Lee", "hash-c", UserRole.SELLER);
        seller.setBio("Selling textbooks");
        seller.setPreferredMeetup(EnumSet.of(Location.LIBRARY, Location.NORTH_GATE));
        Admin admin = new Admin("admin", "Admin", "hash-x", UserRole.ADMIN);
        store.register(buyer);
        store.register(seller);
        store.register(admin);

        UserStore reloaded = reload();

        Buyer alice = assertInstanceOf(Buyer.class, reloaded.find("alice"));
        assertEquals("Alice Chan", alice.getFullName());
        assertEquals("hash-a", alice.getPasswordHash());
        assertEquals("UG Hall V", alice.getResidenceHall());
        assertEquals(created, alice.getCreatedAt());
        assertTrue(alice.isActive());
        assertFalse(alice.isDeleted());

        Seller charlie = assertInstanceOf(Seller.class, reloaded.find("charlie"));
        assertEquals("Selling textbooks", charlie.getBio());
        assertEquals(Set.of(Location.LIBRARY, Location.NORTH_GATE), charlie.getPreferredMeetup());

        assertInstanceOf(Admin.class, reloaded.find("admin"));
        assertEquals(3, reloaded.all().size());
    }

    @Test
    void saveKeepsEditsAcrossAReload() {
        Seller seller = new Seller("charlie", "Charlie Lee", "old-hash", UserRole.SELLER);
        seller.setPreferredMeetup(EnumSet.of(Location.LIBRARY));
        store.register(seller);

        seller.setFullName("Charlie L.");
        seller.setPasswordHash("new-hash");
        seller.setBio("Now selling furniture");
        seller.setPreferredMeetup(EnumSet.of(Location.SOUTH_GATE));
        seller.setActive(false);
        seller.setDeleted(true);
        store.save(seller);

        Seller charlie = (Seller) reload().find("charlie");
        assertEquals("Charlie L.", charlie.getFullName());
        assertEquals("new-hash", charlie.getPasswordHash());
        assertEquals("Now selling furniture", charlie.getBio());
        assertEquals(Set.of(Location.SOUTH_GATE), charlie.getPreferredMeetup());
        assertFalse(charlie.isActive());
        assertTrue(charlie.isDeleted());
    }

    @Test
    void duplicateUsernameKeepsTheFirstAccountAcrossRoles() {
        assertTrue(store.register(new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER)));

        assertFalse(store.register(new Seller("ALICE", "Impostor", "hash", UserRole.SELLER)));

        User alice = reload().find("alice");
        assertInstanceOf(Buyer.class, alice);
        assertEquals("Alice Chan", alice.getFullName());
    }

    @Test
    void buyerWithoutOptionalFieldsReloadsWithNulls() {
        store.register(new Buyer("bob", "Bob Wong", "hash", UserRole.BUYER));

        assertNull(((Buyer) reload().find("bob")).getResidenceHall());
    }

    @Test
    void savingAnUnregisteredAccountIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> store.save(new Buyer("ghost", "Ghost", "hash", UserRole.BUYER)));
    }

    private UserStore reload() {
        UserStore fresh = new UserStore(db);
        fresh.load();
        return fresh;
    }
}
