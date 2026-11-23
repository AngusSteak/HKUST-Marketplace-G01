package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manage Profile (SRS UC-1.7): name and optional password change, saved and reported to the admin. */
class ProfileServiceTest {

    private static final String SEED_PASSWORD = "password1";
    private static final String NEW_PASSWORD = "N3w!Passw0rd";

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final ProfileService profiles = new ProfileService(data);
    private final User alice = data.users().find("alice");

    @Test
    void changingTheNameSavesItAndTellsTheAdminWhichField() {
        List<String> changed = profiles.updateProfile(alice, "  Alice C. Chan ", "", "", "");

        assertEquals(List.of("full name"), changed);
        assertEquals("Alice C. Chan", DummyDataService.open(db).users().find("alice").getFullName());
        Notification notice = adminNotices().get(0);
        assertEquals(NotificationCategory.ACCOUNT_CHANGE, notice.getCategory());
        assertEquals("Buyer @alice updated their profile: full name.", notice.getMessage());
        assertEquals("USER:alice", notice.getRelatedRef().toString());
    }

    @Test
    void changingThePasswordSavesOnlyTheNewHashAndNeverReportsThePassword() {
        List<String> changed = profiles.updateProfile(alice, alice.getFullName(), SEED_PASSWORD,
                NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(List.of("password"), changed);
        String savedHash = DummyDataService.open(db).users().find("alice").getPasswordHash();
        assertTrue(PasswordHasher.verify(NEW_PASSWORD, savedHash));
        assertFalse(PasswordHasher.verify(SEED_PASSWORD, savedHash));
        String message = adminNotices().get(0).getMessage();
        assertEquals("Buyer @alice updated their profile: password.", message);
        assertFalse(message.contains(NEW_PASSWORD) || message.contains(SEED_PASSWORD));
    }

    @Test
    void nameAndPasswordTogether() {
        assertEquals(List.of("full name", "password"),
                profiles.updateProfile(alice, "Alice Chan Tai Man", SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));
        assertEquals("Buyer @alice updated their profile: full name and password.", adminNotices().get(0).getMessage());
    }

    @Test
    void emptyNameIsRefusedAndNothingChanges() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateProfile(alice, "   ", SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(ProfileService.NAME_REQUIRED, e.getMessage());
        assertUnchanged();
    }

    @Test
    void eachPasswordCheckSaysWhatFailedAndChangesNothing() {
        assertFailure(ProfileService.CURRENT_PASSWORD_WRONG, "wrong-password", NEW_PASSWORD, NEW_PASSWORD);
        assertFailure(ProfileService.CURRENT_PASSWORD_WRONG, "", NEW_PASSWORD, NEW_PASSWORD);
        assertFailure(ProfileService.CONFIRMATION_MISMATCH, SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x");

        IllegalArgumentException weak = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateProfile(alice, "Someone Else", SEED_PASSWORD, "short", "short"));
        assertTrue(weak.getMessage().contains("8"), weak.getMessage());

        assertUnchanged();
    }

    @Test
    void fillingOnlyTheConfirmationStillAsksForTheCurrentPassword() {
        assertFailure(ProfileService.CURRENT_PASSWORD_WRONG, "", "", NEW_PASSWORD);
    }

    @Test
    void savingWithoutChangesDoesNothing() {
        assertTrue(profiles.updateProfile(alice, alice.getFullName(), "", "", "").isEmpty());
        assertTrue(adminNotices().isEmpty());
    }

    @Test
    void sellersCanUseItButTheAdminAndDisabledAccountsCannot() {
        User charlie = data.users().find("charlie");
        profiles.updateProfile(charlie, "Charlie Lee Jr", "", "", "");
        assertEquals("Seller @charlie updated their profile: full name.", adminNotices().get(0).getMessage());

        User admin = data.users().find("admin");
        assertThrows(SecurityException.class, () -> profiles.updateProfile(admin, "Boss", "", "", ""));
        alice.setActive(false);
        assertThrows(SecurityException.class, () -> profiles.updateProfile(alice, "Alice", "", "", ""));
    }

    @Test
    void aFailedSaveLeavesNoNoticeOrChangeBehind() {
        data.users().register(new com.hkust.marketplace.models.Admin("admin2", "Second Admin", "hash",
                com.hkust.marketplace.models.UserRole.ADMIN));
        // The second admin's notice fails after the first one was written, so everything rolls back.
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications WHEN NEW.recipient_username = 'admin2'"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(com.hkust.marketplace.persistence.DatabaseException.class,
                () -> profiles.updateProfile(alice, "Alice Renamed", "", "", ""));

        assertUnchanged();
    }

    private void assertFailure(String expected, String current, String newPassword, String confirm) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateProfile(alice, "Someone Else", current, newPassword, confirm));
        assertEquals(expected, e.getMessage());
    }

    private void assertUnchanged() {
        User saved = DummyDataService.open(db).users().find("alice");
        assertEquals("Alice Chan", saved.getFullName());
        assertTrue(PasswordHasher.verify(SEED_PASSWORD, saved.getPasswordHash()));
        assertEquals("Alice Chan", alice.getFullName());
        assertTrue(adminNotices().isEmpty());
    }

    private List<Notification> adminNotices() {
        return data.notifications().forUser("admin", NotificationCategory.ACCOUNT_CHANGE);
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
