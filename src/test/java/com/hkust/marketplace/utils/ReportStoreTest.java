package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportStoreTest {

    private DummyDataService store;
    private Buyer alice;
    private Listing first;
    private Listing second;

    @BeforeEach
    void seedFreshStore() {
        store = new DummyDataService();
        store.seedData();
        alice = (Buyer) store.users().find("alice");
        first = store.listings().find("L100");
        second = store.listings().find("L101");
    }

    @Test
    void filingCreatesAnOpenReportLinkedToBuyerAndListing() {
        ListingReport report = store.reports().fileReport(alice, first, "  Fake listing  ");

        assertEquals("RPT1", report.getId());
        assertEquals(ReportStatus.OPEN, report.getStatus());
        assertEquals("Fake listing", report.getReason());
        assertSame(alice, report.getBuyer());
        assertSame(first, report.getListing());
        assertEquals("alice", report.getReporterUsername());
        assertNotNull(report.getReportedTime());
        assertNull(report.getResolution());
    }

    @Test
    void blankReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> store.reports().fileReport(alice, first, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> store.reports().fileReport(alice, first, null));
        assertTrue(store.reports().all().isEmpty());
    }

    @Test
    void openReportsArePerListingAndExcludeResolvedOnes() {
        ListingReport report = store.reports().fileReport(alice, first, "Spam");
        store.reports().fileReport(alice, second, "Wrong category");

        assertEquals(List.of(report), store.reports().openReports(first));

        report.resolve("Not spam");
        assertTrue(store.reports().openReports(first).isEmpty());
        assertEquals(1, store.reports().openReports(second).size());
    }

    @Test
    void resolveAllClosesEveryOpenReportOnOneListing() {
        store.reports().fileReport(alice, first, "Spam");
        store.reports().fileReport(alice, first, "Duplicate");
        store.reports().fileReport(alice, second, "Wrong category");

        int resolved = store.reports().resolveAll(first, "Listing taken down");

        assertEquals(2, resolved);
        assertTrue(store.reports().openReports(first).isEmpty());
        assertEquals(1, store.reports().openReports(second).size());
        store.reports().all().stream()
                .filter(r -> r.getListing() == first)
                .forEach(r -> {
                    assertEquals(ReportStatus.RESOLVED, r.getStatus());
                    assertEquals("Listing taken down", r.getResolution());
                });
        assertEquals(ReportStatus.OPEN, store.reports().openReports(second).get(0).getStatus());
    }

    @Test
    void resolutionNoteIsRequired() {
        store.reports().fileReport(alice, first, "Spam");

        assertThrows(IllegalArgumentException.class,
                () -> store.reports().resolveAll(first, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> store.reports().all().get(0).resolve(null));
        assertEquals(ReportStatus.OPEN, store.reports().openReports(first).get(0).getStatus());
    }

    @Test
    void allReportsAreKeptInFilingOrder() {
        ListingReport a = store.reports().fileReport(alice, first, "First");
        ListingReport b = store.reports().fileReport(alice, first, "Second");
        a.resolve("Done");

        assertEquals(List.of(a, b), store.reports().all());
    }
}
