package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AuditEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditStoreTest {

    @Test
    void appendRecordsAdminTimeActionAndTarget() {
        AuditStore audit = new AuditStore();
        LocalDateTime before = LocalDateTime.now();

        audit.append("admin", "Kicked off", "L100");

        List<AuditEntry> entries = audit.all();
        assertEquals(1, entries.size());
        AuditEntry entry = entries.get(0);
        assertEquals("admin", entry.getAdmin());
        assertEquals("Kicked off", entry.getAction());
        assertEquals("L100", entry.getTarget());
        assertFalse(entry.getTime().isBefore(before));
        assertFalse(entry.getTime().isAfter(LocalDateTime.now()));
    }

    @Test
    void entriesAreAppendedInOrderAndCannotBeRewritten() {
        AuditStore audit = new AuditStore();
        audit.append("admin", "First", "L100");
        audit.append("admin", "Second", "L101");

        List<AuditEntry> entries = audit.all();
        assertEquals(List.of("First", "Second"),
                entries.stream().map(AuditEntry::getAction).toList());
        assertThrows(UnsupportedOperationException.class, () -> entries.clear());
        assertEquals(2, audit.all().size());
    }

    @Test
    void theFiveAdminActionsHaveStableLabels() {
        AuditStore audit = new AuditStore();
        audit.recordEdit("admin", "L100");
        audit.recordTakeDown("admin", "L101");
        audit.recordReportResolution("admin", "L102");
        audit.recordAddOnBehalf("admin", "L103");
        audit.recordAccountDeletion("admin", "@bob");

        assertEquals(List.of("Edited", "Took down", "Resolved report",
                        "Added listing on behalf", "Deleted account"),
                audit.all().stream().map(AuditEntry::getAction).toList());
        assertTrue(audit.all().stream().allMatch(e -> e.getAdmin().equals("admin")));
    }

    @Test
    void blankFieldsAreRejectedWithoutWritingAnEntry() {
        AuditStore audit = new AuditStore();

        assertThrows(IllegalArgumentException.class, () -> audit.append(null, "Edited", "L100"));
        assertThrows(IllegalArgumentException.class, () -> audit.append("admin", "  ", "L100"));
        assertThrows(IllegalArgumentException.class, () -> audit.append("admin", "Edited", null));

        assertTrue(audit.all().isEmpty());
    }

    @Test
    void dummyDataServiceExposesAnEmptyAuditLog() {
        DummyDataService store = new DummyDataService();
        store.seedData();

        assertTrue(store.audit().all().isEmpty());
    }
}
