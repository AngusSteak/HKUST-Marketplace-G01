package com.hkust.marketplace.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    private static final List<String> TABLES = List.of(
            "users", "seller_meetup_locations", "listings", "listing_photos", "deal_requests",
            "deals", "favourites", "follows", "chat_threads", "messages", "notifications",
            "reviews", "listing_reports", "audit_log");

    @Test
    void schemaCreatesEveryTable() {
        Database db = Database.inMemory();

        List<String> tables = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                row -> row.getString("name"));

        assertTrue(tables.containsAll(TABLES), "missing tables, got " + tables);
        TABLES.forEach(table -> assertTrue(db.isEmpty(table)));
    }

    @Test
    void foreignKeysAreEnforced() {
        Database db = Database.inMemory();

        assertThrows(DatabaseException.class, () -> db.update(
                "INSERT INTO seller_meetup_locations (username, location) VALUES (?, ?)",
                "nobody", "LIBRARY"));
    }

    @Test
    void inMemoryDatabasesAreIsolated() {
        Database first = Database.inMemory();
        Database second = Database.inMemory();
        insertUser(first, "alice");

        assertFalse(first.isEmpty("users"));
        assertTrue(second.isEmpty("users"));
    }

    @Test
    void valuesRoundTripInTheirColumnEncodings() {
        Database db = Database.inMemory();
        LocalDateTime when = LocalDateTime.of(2026, 11, 23, 9, 30, 15);
        insertUser(db, "charlie");
        db.update("INSERT INTO listings (id, type, seller_username, title, description, category, condition,"
                        + " meetup_location, status, submitted_date, view_count, lending_duration_seconds)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "L1", "LEND", "charlie", "Calculator", "", "ELECTRONICS", "USED", "LIBRARY", "LIVE",
                when, 0, Duration.ofSeconds(5));
        db.update("UPDATE users SET active = ? WHERE username = ?", false, "charlie");

        assertEquals(when, db.query("SELECT submitted_date FROM listings",
                row -> Database.dateTime(row, "submitted_date")).get(0));
        assertEquals(Duration.ofSeconds(5), db.query("SELECT lending_duration_seconds FROM listings",
                row -> Database.seconds(row, "lending_duration_seconds")).get(0));
        assertFalse(db.query("SELECT active FROM users", row -> Database.bool(row, "active")).get(0));
        assertEquals(new BigDecimal("12.50"), db.query("SELECT ? AS amount",
                row -> Database.decimal(row, "amount"), new BigDecimal("12.50")).get(0));
    }

    @Test
    void failedTransactionSavesNothing() {
        Database db = Database.inMemory();

        assertThrows(DatabaseException.class, () -> db.inTransaction(() -> {
            insertUser(db, "alice");
            insertUser(db, "alice"); // duplicate primary key
        }));

        assertTrue(db.isEmpty("users"));
    }

    @Test
    void errorInsideTransactionAlsoSavesNothing() {
        Database db = Database.inMemory();

        assertThrows(AssertionError.class, () -> db.inTransaction(() -> {
            insertUser(db, "alice");
            throw new AssertionError("boom");
        }));

        assertTrue(db.isEmpty("users"));
    }

    @Test
    void afterCommitRunsAtOnceOutsideATransactionAndOnlyOnCommitInside() {
        Database db = Database.inMemory();
        List<String> ran = new java.util.ArrayList<>();

        db.afterCommit(() -> ran.add("now"));
        assertEquals(List.of("now"), ran);

        db.inTransaction(() -> {
            db.afterCommit(() -> ran.add("committed"));
            assertEquals(List.of("now"), ran, "waits for the commit");
        });
        assertEquals(List.of("now", "committed"), ran);

        assertThrows(IllegalStateException.class, () -> db.inTransaction(() -> {
            db.afterCommit(() -> ran.add("rolled back"));
            throw new IllegalStateException("boom");
        }));
        db.inTransaction(() -> { });
        assertEquals(List.of("now", "committed"), ran, "a rolled-back action never runs");
    }

    @Test
    void fileDatabaseKeepsDataUnlessReset(@TempDir Path dir) {
        Path file = dir.resolve("nested/marketplace.db");
        try (Database db = Database.openFile(file, false)) {
            insertUser(db, "alice");
        }
        assertTrue(Files.exists(file));

        try (Database reopened = Database.openFile(file, false)) {
            assertFalse(reopened.isEmpty("users"));
        }
        try (Database reset = Database.openFile(file, true)) {
            assertTrue(reset.isEmpty("users"));
        }
    }

    private static void insertUser(Database db, String username) {
        db.update("INSERT INTO users (username, full_name, password_hash, role, created_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                username, username, "hash", "SELLER", LocalDateTime.now());
    }
}
