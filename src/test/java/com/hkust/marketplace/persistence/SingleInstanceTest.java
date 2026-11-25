package com.hkust.marketplace.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only one running copy of the app may use the database file at a time. */
class SingleInstanceTest {

    @TempDir
    Path dir;

    @Test
    void aSecondOpenIsRefusedUntilTheFirstCloses() {
        Path file = dir.resolve("data/marketplace.db");
        Database first = Database.openFile(file, false);

        assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, false));

        first.close();
        Database.openFile(file, false).close();   // free again
    }

    @Test
    void aRefusedResetLeavesTheRunningCopysDataAlone() {
        Path file = dir.resolve("data/marketplace.db");
        try (Database first = Database.openFile(file, false)) {
            first.update("INSERT INTO users (username, full_name, password_hash, role, created_at)"
                    + " VALUES ('alice', 'Alice', 'hash', 'BUYER', ?)", LocalDateTime.now());

            assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, true));

            assertFalse(first.isEmpty("users"), "the second copy did not delete the file");
        }
    }

    @Test
    void anotherProcessHoldingTheFileIsDetected() throws Exception {
        Path file = dir.resolve("data/marketplace.db");
        Process holder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                LockHolder.class.getName(), file.toString())
                .redirectErrorStream(true)
                .start();
        try {
            BufferedReader out = new BufferedReader(
                    new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("OPEN", out.readLine(), "the other process opened the database");

            assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, false));

            holder.getOutputStream().close();   // the other copy exits and its lock is released
            assertTrue(holder.waitFor(20, TimeUnit.SECONDS));
            Database.openFile(file, false).close();
        } finally {
            holder.destroyForcibly();
        }
    }
}
