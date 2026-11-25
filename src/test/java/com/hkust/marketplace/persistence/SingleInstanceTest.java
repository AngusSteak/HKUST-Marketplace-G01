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
        try (Database first = Database.openFile(file, false)) {
            assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, false));
        }
        Database.openFile(file, false).close();   // free again
    }

    @Test
    void aRefusedOpenInTheSameProcessKeepsTheFirstLock() throws Exception {
        Path file = dir.resolve("data/marketplace.db");
        try (Database first = Database.openFile(file, false)) {
            assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, false));

            // On Linux and macOS a file lock belongs to the whole process; the refusal must not
            // release the first copy's lock, so another process must still be kept out.
            assertEquals("IN_USE", runProbe(file));
        }
        assertEquals("ACQUIRED", runProbe(file), "free again once the first copy closes");
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

    /** Runs {@link LockProbe} in a separate JVM and returns what it printed. */
    private static String runProbe(Path file) throws Exception {
        Process probe = javaProcess(LockProbe.class, file);
        try {
            assertTrue(probe.waitFor(30, TimeUnit.SECONDS), "probe finished");
            return new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } finally {
            probe.destroyForcibly();
        }
    }

    private static Process javaProcess(Class<?> main, Path file) throws Exception {
        return new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                main.getName(), file.toString())
                .redirectErrorStream(true)
                .start();
    }

    @Test
    void anotherProcessHoldingTheFileIsDetected() throws Exception {
        Path file = dir.resolve("data/marketplace.db");
        Process holder = javaProcess(LockHolder.class, file);
        try {
            BufferedReader out = new BufferedReader(
                    new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            // Read with a time limit, so a child that never starts fails the test instead of hanging it.
            java.util.concurrent.CompletableFuture<String> firstLine =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return out.readLine();
                        } catch (java.io.IOException e) {
                            return "read failed: " + e;
                        }
                    });
            assertEquals("OPEN", firstLine.get(30, TimeUnit.SECONDS), "the other process opened the database");

            assertThrows(DatabaseInUseException.class, () -> Database.openFile(file, false));

            holder.getOutputStream().close();   // the other copy exits and its lock is released
            assertTrue(holder.waitFor(20, TimeUnit.SECONDS));
            Database.openFile(file, false).close();
        } finally {
            holder.destroyForcibly();
        }
    }
}
