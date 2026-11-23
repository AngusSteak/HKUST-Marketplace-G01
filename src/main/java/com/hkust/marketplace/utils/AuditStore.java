package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AuditEntry;
import com.hkust.marketplace.persistence.Database;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Append-only record of admin actions (SRS UC-3.3 S1, UC-3.7). Entries can be added and read,
 * never edited or removed, so the history stays a faithful record of what was done. Each entry
 * is saved to the {@code audit_log} table as it is appended; {@link #load()} rebuilds them.
 */
public class AuditStore {

    private final List<AuditEntry> entries = new ArrayList<>();
    private final Database db;

    AuditStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public synchronized void append(String admin, String action, String target) {
        requireText(admin, "admin");
        requireText(action, "action");
        requireText(target, "target");
        AuditEntry entry = new AuditEntry(admin, LocalDateTime.now(), action, target);
        db.update("INSERT INTO audit_log (admin, time, action, target) VALUES (?, ?, ?, ?)",
                entry.getAdmin(), entry.getTime(), entry.getAction(), entry.getTarget());
        // Only once saved, so a rolled-back admin action leaves no entry behind.
        db.afterCommit(() -> {
            synchronized (this) {
                entries.add(entry);
            }
        });
    }

    /** Rebuilds the saved log, oldest first. */
    synchronized void load() {
        db.query("SELECT admin, time, action, target FROM audit_log ORDER BY id", row -> {
            AuditEntry entry = new AuditEntry(row.getString("admin"), Database.dateTime(row, "time"),
                    row.getString("action"), row.getString("target"));
            entries.add(entry);
            return entry;
        });
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
    public synchronized List<AuditEntry> all() {
        return List.copyOf(entries);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit " + field + " must not be blank");
        }
    }
}
