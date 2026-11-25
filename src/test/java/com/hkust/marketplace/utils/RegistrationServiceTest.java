package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registration saves the account and tells the admin (SRS UC-1.1 step 7, brief 3.6). */
class RegistrationServiceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final RegistrationService registration = new RegistrationService(data);

    @Test
    void aNewBuyerIsSavedAndTheAdminIsTold() {
        assertTrue(registration.register(new Buyer("erin", "Erin Lau", "hash", UserRole.BUYER)));

        assertNotNull(DummyDataService.open(db).users().find("erin"));
        Notification notice = data.notifications().forUser("admin", NotificationCategory.ACCOUNT_CHANGE).get(0);
        assertEquals("New buyer account registered: Erin Lau (@erin).", notice.getMessage());
        assertEquals("USER:erin", notice.getRelatedRef().toString());
    }

    @Test
    void aNewSellerIsAnnouncedAsASeller() {
        registration.register(new Seller("frank", "Frank Ho", "hash", UserRole.SELLER));

        assertEquals("New seller account registered: Frank Ho (@frank).",
                data.notifications().forUser("admin", NotificationCategory.ACCOUNT_CHANGE).get(0).getMessage());
    }

    @Test
    void aTakenUsernameIsRefusedAndNobodyIsTold() {
        assertFalse(registration.register(new Seller("ALICE", "Impostor", "hash", UserRole.SELLER)));

        assertTrue(data.notifications().forUser("admin", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
    }

    @Test
    void adminAccountsCannotRegister() {
        assertThrows(IllegalArgumentException.class,
                () -> registration.register(new Admin("root", "Root", "hash", UserRole.ADMIN)));
        assertEquals(null, data.users().find("root"));
    }

    @Test
    void aFailedNoticeDoesNotUndoTheRegistration() {
        db.update("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications"
                + " BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertTrue(registration.register(new Buyer("erin", "Erin Lau", "hash", UserRole.BUYER)));

        assertNotNull(DummyDataService.open(db).users().find("erin"), "the account can still log in");
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
