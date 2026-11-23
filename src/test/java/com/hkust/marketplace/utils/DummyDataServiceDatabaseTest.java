package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Startup against a database file: seed once when empty, otherwise load what was saved. */
class DummyDataServiceDatabaseTest {

    @TempDir
    Path dir;

    @Test
    void freshDatabaseIsSeededAndSavesTheDemoUsersAndListings() {
        DummyDataService data = DummyDataService.open(Database.openFile(file(), false));

        assertEquals(5, data.users().all().size());
        assertEquals(5, data.listings().all().size());
        assertEquals(5, data.database().query("SELECT COUNT(*) FROM users", r -> r.getInt(1)).get(0));
        assertEquals(5, data.database().query("SELECT COUNT(*) FROM listings", r -> r.getInt(1)).get(0));
        data.database().close();
    }

    @Test
    void reopeningKeepsChangesAndDoesNotSeedTwice() {
        DummyDataService first = DummyDataService.open(Database.openFile(file(), false));
        first.users().register(new Buyer("carol", "Carol Cheung", PasswordHasher.hash("Str0ng!Pass"), UserRole.BUYER));
        Listing fridge = first.listings().find("L104");
        fridge.setStatus(ListingStatus.LIVE);
        fridge.setReviewedAt(LocalDateTime.of(2026, 11, 23, 12, 0));
        first.listings().update(fridge);
        first.users().find("bob").setActive(false);
        first.users().save(first.users().find("bob"));
        first.database().close();

        DummyDataService second = DummyDataService.open(Database.openFile(file(), false));

        assertEquals(6, second.users().all().size(), "no second seed");
        assertEquals(5, second.listings().all().size());
        assertNotNull(second.users().find("carol"));
        assertTrue(PasswordHasher.verify("Str0ng!Pass", second.users().find("carol").getPasswordHash()));
        assertFalse(second.users().find("bob").isActive());
        assertEquals(ListingStatus.LIVE, second.listings().find("L104").getStatus());
        assertEquals(LocalDateTime.of(2026, 11, 23, 12, 0), second.listings().find("L104").getReviewedAt());
        assertEquals("Diana Ng", second.listings().find("L104").getSellerFullName());
        second.database().close();
    }

    @Test
    void resetStartsAgainFromTheDemoData() {
        DummyDataService first = DummyDataService.open(Database.openFile(file(), false));
        first.users().register(new Buyer("carol", "Carol Cheung", "hash", UserRole.BUYER));
        first.database().close();

        DummyDataService reset = DummyDataService.open(Database.openFile(file(), true));

        assertNull(reset.users().find("carol"));
        assertEquals(5, reset.users().all().size());
        reset.database().close();
    }

    private Path file() {
        return dir.resolve("data/marketplace.db");
    }
}
