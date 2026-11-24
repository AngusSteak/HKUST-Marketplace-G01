package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletedVisibilityTest {

    private final Database db = Database.inMemory();
    private DummyDataService data;
    private Listing hiddenLive;
    private Listing hiddenPending;

    @BeforeEach
    void hideOneLiveAndOnePendingListing() {
        data = new DummyDataService(db);
        data.seedData();
        hiddenLive = data.listings().find("L101");
        hiddenPending = data.listings().find("L104");
        hiddenLive.setDeleted(true);
        hiddenPending.setDeleted(true);
        data.listings().update(hiddenLive);
        data.listings().update(hiddenPending);
    }

    @Test
    void hiddenListingsAreAbsentFromEveryCollectionButResolvableById() {
        assertEquals(3, data.listings().all().size());
        assertFalse(data.listings().all().contains(hiddenLive));
        assertFalse(data.listings().all().contains(hiddenPending));

        assertFalse(data.listings().live().contains(hiddenLive));
        assertEquals(1, data.listings().live().size());
        assertFalse(data.listings().pending().contains(hiddenPending));
        assertTrue(data.listings().pending().isEmpty());

        assertFalse(data.listings().bySeller("charlie").contains(hiddenLive));
        assertEquals(1, data.listings().bySeller("charlie").size());
        assertFalse(data.listings().bySeller("diana").contains(hiddenPending));
        assertEquals(2, data.listings().bySeller("diana").size());

        List<Listing> found = data.listings().findAll(List.of("L101", "L104", "L100", "L999"));
        assertEquals(List.of("L100"), found.stream().map(Listing::getId).toList());

        assertSame(hiddenLive, data.listings().find("L101"));
        assertSame(hiddenPending, data.listings().find("L104"));
    }

    @Test
    void hiddenListingsStayHiddenAfterAReopenButStillResolveById() {
        DummyDataService reopened = DummyDataService.open(db);
        Listing liveBack = reopened.listings().find("L101");
        Listing pendingBack = reopened.listings().find("L104");

        assertTrue(liveBack.isDeleted());
        assertTrue(pendingBack.isDeleted());
        assertEquals(3, reopened.listings().all().size());
        assertFalse(reopened.listings().all().contains(liveBack));
        assertFalse(reopened.listings().live().contains(liveBack));
        assertFalse(reopened.listings().bySeller("charlie").contains(liveBack));
        assertTrue(reopened.listings().pending().isEmpty());
        assertFalse(reopened.listings().findAll(List.of("L101", "L104")).contains(liveBack));
        assertSame(liveBack, reopened.listings().find("L101"));
        assertSame(pendingBack, reopened.listings().find("L104"));
    }

    @Test
    void visibleListingsStayVisible() {
        Listing calculator = data.listings().find("L103");
        assertFalse(calculator.isDeleted());
        assertTrue(data.listings().all().contains(calculator));
        assertTrue(data.listings().live().contains(calculator));
        assertTrue(data.listings().bySeller("diana").contains(calculator));
        assertTrue(data.listings().findAll(List.of("L103")).contains(calculator));
        assertEquals(calculator, data.listings().find("L103"));
    }
}
