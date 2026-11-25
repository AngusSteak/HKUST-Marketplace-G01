package com.hkust.marketplace.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The application's SQLite database: one JDBC connection, foreign keys on, and the schema in
 * {@code src/main/resources/db/schema.sql} applied on open.
 *
 * <p>Stores keep their objects in memory and write every change through this class, so the
 * database always holds the current state and is read back once at startup. All methods are
 * synchronized because the single connection is shared by the UI thread and background timers.
 *
 * <p>Value encoding: timestamps are ISO-8601 TEXT, money is decimal TEXT, durations are whole
 * seconds, booleans are INTEGER 0/1 (see {@link #param}).
 */
public final class Database implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private final Connection connection;
    /** Work to run once the current transaction commits; dropped if it rolls back. */
    private final List<Runnable> afterCommit = new ArrayList<>();
    /** The lock that keeps a second running copy of the app off this file; {@code null} in memory. */
    private final FileChannel lockChannel;

    private Database(String url) {
        this(url, null);
    }

    private Database(String url, FileChannel lockChannel) {
        this.lockChannel = lockChannel;
        try {
            this.connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            applySchema();
        } catch (SQLException e) {
            throw new DatabaseException("Could not open database " + url, e);
        }
    }

    /**
     * Opens (creating if needed) the database file. With {@code reset} the existing file is
     * deleted first, so the app starts from a fresh, re-seeded database.
     *
     * <p>Only one running copy of the app may use the file at a time: an operating-system lock
     * on {@code <file>.lock} is taken first and held until {@link #close()} (the system also
     * releases it if the app crashes). The lock is taken before a reset, so a second copy can
     * never delete the file under the first.
     *
     * @throws DatabaseInUseException if another running copy already has the file open
     */
    public static Database openFile(Path file, boolean reset) {
        Objects.requireNonNull(file, "file");
        FileChannel lock;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            lock = lock(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare database file " + file, e);
        }
        try {
            if (reset) {
                Files.deleteIfExists(file);
            }
            return new Database("jdbc:sqlite:" + file.toAbsolutePath(), lock);
        } catch (IOException | RuntimeException e) {
            closeQuietly(lock);
            if (e instanceof IOException io) {
                throw new UncheckedIOException("Could not prepare database file " + file, io);
            }
            throw (RuntimeException) e;
        }
    }

    /** Takes the lock on {@code <file>.lock}, or refuses if another copy holds it. */
    private static FileChannel lock(Path file) throws IOException {
        Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;   // already open in this same process
        }
        if (lock == null) {
            channel.close();
            throw new DatabaseInUseException(file);
        }
        return channel;
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();   // closing the channel releases its lock
        } catch (IOException ignored) {
            // nothing more to release
        }
    }

    /** A private, empty database that lives only as long as this object; used by tests. */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    /** Runs an INSERT, UPDATE or DELETE and returns the number of rows changed. */
    public synchronized int update(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Update failed: " + sql, e);
        }
    }

    /** Runs a SELECT and maps every row. */
    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rows = statement.executeQuery()) {
            List<T> results = new ArrayList<>();
            while (rows.next()) {
                results.add(mapper.map(rows));
            }
            return results;
        } catch (SQLException e) {
            throw new DatabaseException("Query failed: " + sql, e);
        }
    }

    /** True when {@code table} has no rows. */
    public boolean isEmpty(String table) {
        if (!table.matches("[a-z_]+")) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        return query("SELECT COUNT(*) FROM " + table, rows -> rows.getLong(1)).get(0) == 0;
    }

    /**
     * Runs {@code work} as one transaction: every write inside it is saved together or, if it
     * throws, none are. Nested calls join the outer transaction.
     */
    public synchronized void inTransaction(Runnable work) {
        try {
            if (!connection.getAutoCommit()) {
                work.run();
                return;
            }
            connection.setAutoCommit(false);
            try {
                work.run();
                connection.commit();
            } catch (RuntimeException | Error e) {
                afterCommit.clear();
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            afterCommit.clear();
            throw new DatabaseException("Transaction failed", e);
        }
        List<Runnable> committed = new ArrayList<>(afterCommit);
        afterCommit.clear();
        committed.forEach(Runnable::run);
    }

    /**
     * Runs {@code action} once the data it depends on is saved: straight away outside a
     * transaction, or when the current transaction commits. If the transaction rolls back the
     * action never runs. Stores use it to update their in-memory copy only for saved rows.
     */
    public synchronized void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        try {
            if (connection.getAutoCommit()) {
                action.run();
            } else {
                afterCommit.add(action);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not read the transaction state", e);
        }
    }

    /** Closes the database connection. */
    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseException("Could not close database", e);
        } finally {
            if (lockChannel != null) {
                closeQuietly(lockChannel);
            }
        }
    }

    // ---------- Reading helpers for RowMappers ----------

    /**
     * Reads an ISO date-time column, or {@code null}.
     *
     * @throws SQLException if the column cannot be read
     */
    public static LocalDateTime dateTime(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : LocalDateTime.parse(value);
    }

    /**
     * Reads a decimal column, or {@code null}.
     *
     * @throws SQLException if the column cannot be read
     */
    public static BigDecimal decimal(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : new BigDecimal(value);
    }

    /**
     * Reads a duration stored as whole seconds, or {@code null}.
     *
     * @throws SQLException if the column cannot be read
     */
    public static Duration seconds(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : Duration.ofSeconds(value);
    }

    /**
     * Reads a 0/1 column as a boolean.
     *
     * @throws SQLException if the column cannot be read
     */
    public static boolean bool(ResultSet rows, String column) throws SQLException {
        return rows.getInt(column) != 0;
    }

    // ---------- Internals ----------

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, param(params[i]));
        }
        return statement;
    }

    /** Converts Java values to the column encodings described on the class. */
    private static Object param(Object value) {
        if (value instanceof LocalDateTime time) return time.toString();
        if (value instanceof BigDecimal amount) return amount.toPlainString();
        if (value instanceof Duration duration) return duration.getSeconds();
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof Enum<?> constant) return constant.name();
        return value;
    }

    private void applySchema() throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + SCHEMA_RESOURCE + " on the classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SCHEMA_RESOURCE, e);
        }
        // Simple split: schema.sql must not contain ';' or '--' inside string literals.
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.replaceAll("(?m)--.*$", "").split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /**
     * Maps the current row of a result set to an object.
     *
     * @param <T> the mapped type
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        /**
         * Maps the current row to one object.
         *
         * @throws SQLException if the row cannot be read
         */
        T map(ResultSet row) throws SQLException;
    }
}
