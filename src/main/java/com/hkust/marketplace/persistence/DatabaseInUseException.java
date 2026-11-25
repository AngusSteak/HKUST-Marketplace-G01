package com.hkust.marketplace.persistence;

import java.nio.file.Path;

/**
 * Thrown when the database file is already open in another running copy of the app. Each copy
 * keeps the data in memory and only writes changes back, so two copies on one file would not
 * see each other's changes and could overwrite them; only one may run at a time.
 */
public class DatabaseInUseException extends RuntimeException {

    /** The database file is locked by another running copy of the app. */
    public DatabaseInUseException(Path file) {
        super("The database " + file + " is already in use by another running copy of the app.");
    }
}
