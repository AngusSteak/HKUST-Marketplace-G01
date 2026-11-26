package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smaller rules in the admin deal filters (SRS UC-3.5), the notification store (UC-1.8) and the
 * report store (UC-3.7). Seed data: D1 is alice's active purchase, D2 bob's completed loan, and
 * Q4 bob's pending borrow of L103.
 */
class StoreAndFilterBranchTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();

    // ---------- Admin deal filters ----------

    @Test
    void typeAndStatusFiltersMustMatchExactly() {
        Deal purchase = deal("D1");

        assertTrue(AdminFilters.matchesDeal(purchase, null, null, null, null, null));
        assertTrue(AdminFilters.matchesDeal(purchase, "  ", "SOLD", "ACTIVE", null, null));
        assertFalse(AdminFilters.matchesDeal(purchase, null, null, "COMPLETED", null, null));
    }

    @Test
    void aDealMatchesADateRangeByItsStartOrItsEnd() {
        Deal loan = deal("D2");
        LocalDate start = loan.getStartDate().toLocalDate();
        LocalDate end = loan.getCompletedDate().toLocalDate();

        assertTrue(AdminFilters.matchesDeal(loan, null, null, null, start, start));
        assertTrue(AdminFilters.matchesDeal(loan, null, null, null, end, end), "matched by the end date");
        assertFalse(AdminFilters.matchesDeal(loan, null, null, null, end.plusDays(1), null));
    }

    @Test
    void anActiveLoanIsMatchedByItsDueDate() {
        DealRequest borrow = data.deals().lifecycle().findRequest("Q4");
        Deal loan = data.deals().lifecycle().accept(borrow);
        LocalDate due = loan.getDueDate().toLocalDate();

        assertTrue(AdminFilters.matchesDeal(loan, null, null, null, due, due));
        // A sale has neither a completed nor a due date: only its start can match.
        Deal purchase = deal("D1");
        LocalDate bought = purchase.getStartDate().toLocalDate();
        assertFalse(AdminFilters.matchesDeal(purchase, null, null, null, bought.plusDays(1), null));
    }

    @Test
    void onlyAStartAfterTheEndIsAnInvalidRange() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        assertTrue(AdminFilters.isValidDealDateRange(null, null));
        assertTrue(AdminFilters.isValidDealDateRange(day, null));
        assertTrue(AdminFilters.isValidDealDateRange(null, day));
        assertTrue(AdminFilters.isValidDealDateRange(day, day));
        assertFalse(AdminFilters.isValidDealDateRange(day.plusDays(1), day));
    }

    // ---------- Notifications ----------

    @Test
    void aNoticeNeedsContent() {
        NotificationStore store = data.notifications();
        assertThrows(IllegalArgumentException.class,
                () -> store.notify("alice", NotificationCategory.ANNOUNCEMENT, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> store.notify("alice", NotificationCategory.ANNOUNCEMENT, " ", null));
    }

    @Test
    void aNoticeWithItsOwnIdKeepsItAndOddIdsSortLast() {
        NotificationStore store = data.notifications();
        Notification named = new Notification("alice", "Hi", "Custom id", NotificationCategory.ANNOUNCEMENT);
        named.setId("custom");
        Notification odd = new Notification("alice", "Hi", "Odd id", NotificationCategory.ANNOUNCEMENT);
        odd.setId("Nx");
        named.setTimestamp(odd.getTimestamp());

        store.add(named);
        store.add(odd);

        List<String> ids = store.forUser("alice").stream().map(Notification::getId).toList();
        assertTrue(ids.contains("custom"));
        assertTrue(ids.contains("Nx"));
    }

    @Test
    void markingAnUnknownNoticeReadIsRefused() {
        Notification ghost = new Notification("alice", "Hi", "Never saved", NotificationCategory.ANNOUNCEMENT);
        ghost.setId("N9999");

        assertThrows(IllegalArgumentException.class, () -> data.notifications().markRead(ghost, true));
        assertFalse(ghost.isRead());
    }

    // ---------- Reports ----------

    @Test
    void reportsNeedANoteAndMustBeKnown() {
        ReportStore reports = data.reports();
        Buyer alice = (Buyer) data.users().find("alice");
        ListingReport filed = reports.fileReport(alice, data.listings().find("L101"), "Looks fake");
        ListingReport stranger = new ListingReport("RX", alice, data.listings().find("L101"), "Never filed");

        assertThrows(IllegalArgumentException.class, () -> reports.resolve(stranger, "Checked"));
        assertThrows(IllegalArgumentException.class, () -> reports.resolve(filed, " "));
        assertThrows(IllegalArgumentException.class, () -> reports.resolveAll(data.listings().find("L101"), null));
        assertEquals(1, reports.openReports(data.listings().find("L101")).size());

        reports.resolve(filed, "Checked");
        assertTrue(reports.openReports(data.listings().find("L101")).isEmpty());
        assertEquals(1, DummyDataService.open(db).reports().all().size(), "the report is reloaded from the database");
    }

    private Deal deal(String id) {
        return data.deals().allDeals().stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
