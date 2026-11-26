package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Profile edits for buyers (SRS UC-1.7) and sellers (UC-2.6) beyond the main flow. */
class ProfileServiceBranchTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final ProfileService profiles = new ProfileService(data);
    private final User alice = data.users().find("alice");
    private final Seller charlie = (Seller) data.users().find("charlie");

    @Test
    void aMissingNameIsRefusedForBothRoles() {
        assertEquals(ProfileService.NAME_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> profiles.updateProfile(alice, null, null, null, null)).getMessage());
        assertEquals(ProfileService.NAME_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, null, null, null, null, null, null)).getMessage());
        assertEquals(ProfileService.NAME_REQUIRED, assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, "  ", null, null, null, null, null)).getMessage());
    }

    @Test
    void aBuyerCannotUseTheSellerProfile() {
        assertThrows(SecurityException.class,
                () -> profiles.updateSellerProfile(alice, "Alice", null, null, null, null, null));
    }

    @Test
    void aMissingBioAndMeetupClearThemAndABlankBioCountsAsNone() {
        charlie.setBio("   ");
        charlie.setPreferredMeetup(EnumSet.of(Location.LIBRARY));

        List<String> changed = profiles.updateSellerProfile(charlie, "Charlie Lee", null, null, null, null, null);

        assertEquals(List.of("preferred meet-up locations"), changed, "a blank bio was already no bio");
        assertNull(charlie.getBio());
        assertTrue(charlie.getPreferredMeetup().isEmpty());
    }

    @Test
    void aTooLongBioIsRefused() {
        assertEquals(ProfileService.BIO_TOO_LONG, assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, "Charlie Lee", "x".repeat(501), null, null, null, null))
                .getMessage());
    }

    @Test
    void anySinglePasswordFieldStartsASellerPasswordChange() {
        for (String[] fields : new String[][]{{"password1", "", ""}, {"", "NewPass1!", ""}, {"", "", "NewPass1!"}}) {
            assertThrows(IllegalArgumentException.class, () -> profiles.updateSellerProfile(charlie, "Charlie Lee",
                    null, null, fields[0], fields[1], fields[2]));
        }
        assertEquals(List.of("password"), profiles.updateSellerProfile(charlie, "Charlie Lee", charlie.getBio(),
                charlie.getPreferredMeetup(), "password1", "NewPass1!", "NewPass1!"));
    }

    @Test
    void aPlainEmptyMeetupSetIsAccepted() {
        charlie.setPreferredMeetup(EnumSet.of(Location.LIBRARY));

        profiles.updateSellerProfile(charlie, "Charlie Lee", charlie.getBio(), Set.of(), null, null, null);

        assertTrue(charlie.getPreferredMeetup().isEmpty());
    }

    @Test
    void aFailedSellerSaveRestoresTheOldMeetupPlaces() {
        charlie.setPreferredMeetup(EnumSet.of(Location.LIBRARY, Location.NORTH_GATE));
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class, () -> profiles.updateSellerProfile(charlie, "Renamed", "New bio",
                EnumSet.of(Location.SOUTH_GATE), null, null, null));

        assertEquals("Charlie Lee", charlie.getFullName());
        assertEquals(EnumSet.of(Location.LIBRARY, Location.NORTH_GATE), charlie.getPreferredMeetup());
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
