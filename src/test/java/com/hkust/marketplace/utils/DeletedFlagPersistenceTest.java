package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletedFlagPersistenceTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;

    @BeforeEach
    void seedFreshStore() {
        data = new DummyDataService(db);
        data.seedData();
    }

    @Test
    void hiddenAndVisibleFlagsBothSurviveAReopen() {
        Listing hidden = data.listings().find("L100");
        Listing visible = data.listings().find("L101");
        hidden.setStatus(ListingStatus.REJECTED);
        hidden.setRejectionReason("Blurry photos");
        hidden.setDeleted(true);
        data.listings().update(hidden);
        visible.setDeleted(false);
        data.listings().update(visible);

        DummyDataService reopened = DummyDataService.open(db);

        Listing hiddenBack = reopened.listings().find("L100");
        assertTrue(hiddenBack.isDeleted());
        assertEquals(ListingStatus.REJECTED, hiddenBack.getStatus());
        assertEquals("Blurry photos", hiddenBack.getRejectionReason());
        assertFalse(reopened.listings().find("L101").isDeleted());
        assertSame(hiddenBack, reopened.listings().find("L100"));
    }

    @Test
    void aListingAddedWithoutSettingTheFlagReloadsAsVisible() {
        Listing fresh = new SaleListing((Seller) data.users().find("charlie"), "Fresh", "",
                Category.OTHERS, Condition.NEW, Location.LIBRARY, Money.of("10"));
        data.listings().add(fresh);

        assertFalse(fresh.isDeleted());
        assertFalse(DummyDataService.open(db).listings().find(fresh.getId()).isDeleted());
    }

    @Test
    void theColumnDefaultIsZeroForARowInsertedWithoutTheFlag() {
        db.update("INSERT INTO listings (id, type, seller_username, title, description, category,"
                        + " condition, meetup_location, status, submitted_date, price)"
                        + " VALUES ('L900', 'SELL', 'charlie', 'Direct row', '', 'OTHERS', 'USED',"
                        + " 'LIBRARY', 'LIVE', '2026-11-01T09:00:00', '1.00')");

        assertFalse(DummyDataService.open(db).listings().find("L900").isDeleted());
    }

    @Test
    void theFlagSurvivesASecondReopen() {
        Listing hidden = data.listings().find("L100");
        hidden.setDeleted(true);
        data.listings().update(hidden);

        DummyDataService first = DummyDataService.open(db);
        assertTrue(first.listings().find("L100").isDeleted());

        DummyDataService second = DummyDataService.open(db);
        assertTrue(second.listings().find("L100").isDeleted());
    }
}
