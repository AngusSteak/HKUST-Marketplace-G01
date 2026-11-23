package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AuditEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only record of admin actions (SRS UC-3.3 S1, UC-3.7). Entries can be added and read,
 * never edited or removed, so the history stays a faithful record of what was done.
 */
public class AuditStore {

    private final List<AuditEntry> entries = new ArrayList<>();

    public void append(String admin, String action, String target) {
        requireText(admin, "admin");
        requireText(action, "action");
        requireText(target, "target");
        entries.add(new AuditEntry(admin, LocalDateTime.now(), action, target));
    }

    public void recordEdit(String admin, String target) {
        append(admin, "Edited", target);
    }

    public void recordTakeDown(String admin, String target) {
        append(admin, "Took down", target);
    }

    public void recordReportResolution(String admin, String target) {
        append(admin, "Resolved report", target);
    }

    public void recordAddOnBehalf(String admin, String target) {
        append(admin, "Added listing on behalf", target);
    }

    public void recordAccountDeletion(String admin, String target) {
        append(admin, "Deleted account", target);
    }

    /** Oldest first; an unmodifiable snapshot so callers cannot rewrite history. */
    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit " + field + " must not be blank");
        }
    }
}
