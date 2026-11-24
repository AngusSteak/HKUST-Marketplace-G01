package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final User charlie = data.users().find("charlie");

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

    @Test
    void bioOnlyChangeIsTrimmedAndPersisted() {
        List<String> changed = profiles.updateSellerProfile(charlie, charlie.getFullName(),
                "  I sell textbooks and gadgets.  ", currentMeetups(), "", "", "");

        assertEquals(List.of("bio"), changed);
        assertEquals("I sell textbooks and gadgets.", ((Seller) charlie).getBio());
        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertEquals("I sell textbooks and gadgets.", reloaded.getBio());
    }

    @Test
    void meetupsOnlyChangeIsPersistedAndReloaded() {
        Set<Location> chosen = EnumSet.of(Location.LIBRARY, Location.UG_HALL_V);
        List<String> changed = profiles.updateSellerProfile(charlie, charlie.getFullName(),
                currentBio(), chosen, "", "", "");

        assertEquals(List.of("preferred meet-up locations"), changed);
        assertEquals(chosen, ((Seller) charlie).getPreferredMeetup());
        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertEquals(chosen, reloaded.getPreferredMeetup());
    }

    @Test
    void clearingAllMeetupsPersistsAnEmptySet() {
        profiles.updateSellerProfile(charlie, charlie.getFullName(), currentBio(),
                EnumSet.of(Location.LIBRARY, Location.UG_HALL_V), "", "", "");

        List<String> changed = profiles.updateSellerProfile(charlie, charlie.getFullName(),
                currentBio(), null, "", "", "");

        assertEquals(List.of("preferred meet-up locations"), changed);
        assertTrue(((Seller) charlie).getPreferredMeetup().isEmpty());
        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertTrue(reloaded.getPreferredMeetup().isEmpty());
    }

    @Test
    void nameOnlyChange() {
        List<String> changed = profiles.updateSellerProfile(charlie, "  Charlie Lee Jr  ",
                currentBio(), currentMeetups(), "", "", "");

        assertEquals(List.of("full name"), changed);
        assertEquals("Charlie Lee Jr", DummyDataService.open(db).users().find("charlie").getFullName());
    }

    @Test
    void combinedChangeReportsFieldsInFixedOrder() {
        List<String> changed = profiles.updateSellerProfile(charlie, "Charlie Lee Jr", "  New bio here.  ",
                EnumSet.of(Location.LIBRARY, Location.UG_HALL_V),
                SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(List.of("full name", "bio", "preferred meet-up locations", "password"), changed);
        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertEquals("Charlie Lee Jr", reloaded.getFullName());
        assertEquals("New bio here.", reloaded.getBio());
        assertEquals(EnumSet.of(Location.LIBRARY, Location.UG_HALL_V), reloaded.getPreferredMeetup());
        assertTrue(PasswordHasher.verify(NEW_PASSWORD, reloaded.getPasswordHash()));
    }

    @Test
    void blankBioBecomesNullAndOversizedBioChangesNothing() {
        List<String> blank = profiles.updateSellerProfile(charlie, charlie.getFullName(),
                "   ", currentMeetups(), "", "", "");
        assertEquals(List.of("bio"), blank);
        assertNull(((Seller) charlie).getBio());
        assertNull(((Seller) DummyDataService.open(db).users().find("charlie")).getBio());

        String nameBefore = charlie.getFullName();
        String hashBefore = charlie.getPasswordHash();
        Set<Location> meetupsBefore = EnumSet.copyOf(((Seller) charlie).getPreferredMeetup());
        String longBio = "x".repeat(ProfileService.BIO_MAX_LENGTH + 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, "Different Name", longBio,
                        EnumSet.of(Location.SOUTH_GATE), "", "", ""));
        assertEquals(ProfileService.BIO_TOO_LONG, e.getMessage());

        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertEquals(nameBefore, reloaded.getFullName());
        assertNull(reloaded.getBio());
        assertEquals(meetupsBefore, reloaded.getPreferredMeetup());
        assertEquals(hashBefore, reloaded.getPasswordHash());
        assertEquals(nameBefore, charlie.getFullName());
        assertNull(((Seller) charlie).getBio());
        assertEquals(meetupsBefore, ((Seller) charlie).getPreferredMeetup());
        assertEquals(hashBefore, charlie.getPasswordHash());
        assertEquals(1, adminNotices().size());
    }

    @Test
    void passwordChangeValidatesCurrentStrengthAndConfirmation() {
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, charlie.getFullName(), currentBio(), currentMeetups(),
                        "wrong-password", NEW_PASSWORD, NEW_PASSWORD));
        assertEquals(ProfileService.CURRENT_PASSWORD_WRONG, wrong.getMessage());
        assertSellerPasswordUnchanged();

        IllegalArgumentException weak = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, charlie.getFullName(), currentBio(), currentMeetups(),
                        SEED_PASSWORD, "short", "short"));
        assertTrue(weak.getMessage().contains("8"), weak.getMessage());
        assertSellerPasswordUnchanged();

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> profiles.updateSellerProfile(charlie, charlie.getFullName(), currentBio(), currentMeetups(),
                        SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x"));
        assertEquals(ProfileService.CONFIRMATION_MISMATCH, mismatch.getMessage());
        assertSellerPasswordUnchanged();

        List<String> changed = profiles.updateSellerProfile(charlie, charlie.getFullName(), currentBio(), currentMeetups(),
                SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals(List.of("password"), changed);
        String savedHash = ((Seller) DummyDataService.open(db).users().find("charlie")).getPasswordHash();
        assertTrue(PasswordHasher.verify(NEW_PASSWORD, savedHash));
        assertFalse(PasswordHasher.verify(SEED_PASSWORD, savedHash));
    }

    @Test
    void failedSaveRestoresNameHashBioAndMeetupsAndLeavesNoNotice() {
        data.users().register(new com.hkust.marketplace.models.Admin("admin2", "Second Admin", "hash",
                com.hkust.marketplace.models.UserRole.ADMIN));
        // The second admin's notice fails after the first one was written, so everything rolls back.
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications WHEN NEW.recipient_username = 'admin2'"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        Seller seller = (Seller) charlie;
        String oldName = charlie.getFullName();
        String oldHash = charlie.getPasswordHash();
        String oldBio = seller.getBio();
        Set<Location> oldMeetup = EnumSet.copyOf(seller.getPreferredMeetup());

        assertThrows(com.hkust.marketplace.persistence.DatabaseException.class,
                () -> profiles.updateSellerProfile(charlie, "Charlie Renamed", "  New bio  ",
                        EnumSet.of(Location.LIBRARY, Location.UG_HALL_V),
                        SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(oldName, charlie.getFullName());
        assertEquals(oldHash, charlie.getPasswordHash());
        assertEquals(oldBio, seller.getBio());
        assertEquals(oldMeetup, seller.getPreferredMeetup());

        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertEquals(oldName, reloaded.getFullName());
        assertEquals(oldHash, reloaded.getPasswordHash());
        assertEquals(oldBio, reloaded.getBio());
        assertEquals(oldMeetup, reloaded.getPreferredMeetup());

        assertTrue(adminNotices().isEmpty());
        assertTrue(data.notifications().forUser("admin2", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
    }

    @Test
    void everyAdminGetsOneNoticeListingChangedFieldsAndNeverThePassword() {
        data.users().register(new com.hkust.marketplace.models.Admin("admin2", "Second Admin", "hash",
                com.hkust.marketplace.models.UserRole.ADMIN));

        List<String> changed = profiles.updateSellerProfile(charlie, "Charlie Lee Jr", "  New bio  ",
                EnumSet.of(Location.LIBRARY, Location.UG_HALL_V),
                SEED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals(List.of("full name", "bio", "preferred meet-up locations", "password"), changed);

        for (String adminUsername : List.of("admin", "admin2")) {
            List<Notification> notices = data.notifications().forUser(adminUsername, NotificationCategory.ACCOUNT_CHANGE);
            assertEquals(1, notices.size(), adminUsername);
            Notification notice = notices.get(0);
            assertEquals(NotificationCategory.ACCOUNT_CHANGE, notice.getCategory());
            for (String field : changed) {
                assertTrue(notice.getMessage().contains(field), notice.getMessage());
            }
            assertFalse(notice.getMessage().contains(NEW_PASSWORD));
            assertFalse(notice.getMessage().contains(SEED_PASSWORD));
        }

        Seller seller = (Seller) charlie;
        assertTrue(profiles.updateSellerProfile(charlie, charlie.getFullName(), seller.getBio(),
                seller.getPreferredMeetup(), "", "", "").isEmpty());
        assertEquals(1, data.notifications().forUser("admin", NotificationCategory.ACCOUNT_CHANGE).size());
        assertEquals(1, data.notifications().forUser("admin2", NotificationCategory.ACCOUNT_CHANGE).size());
    }

    private String currentBio() {
        return ((Seller) charlie).getBio();
    }

    private Set<Location> currentMeetups() {
        return ((Seller) charlie).getPreferredMeetup();
    }

    private void assertSellerPasswordUnchanged() {
        Seller reloaded = (Seller) DummyDataService.open(db).users().find("charlie");
        assertTrue(PasswordHasher.verify(SEED_PASSWORD, reloaded.getPasswordHash()));
        assertTrue(PasswordHasher.verify(SEED_PASSWORD, charlie.getPasswordHash()));
        assertTrue(adminNotices().isEmpty());
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
