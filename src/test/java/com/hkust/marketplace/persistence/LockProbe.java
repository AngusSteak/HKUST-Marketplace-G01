package com.hkust.marketplace.persistence;

import java.nio.file.Path;

/**
 * Test helper run as a separate process: tries to open the database file once and prints
 * "ACQUIRED" (and closes it again) or "IN_USE", then exits. Stands in for a second copy of the app.
 */
public final class LockProbe {

    private LockProbe() {
    }

    public static void main(String[] args) {
        try {
            Database.openFile(Path.of(args[0]), false).close();
            System.out.println("ACQUIRED");
        } catch (DatabaseInUseException e) {
            System.out.println("IN_USE");
        }
    }
}
