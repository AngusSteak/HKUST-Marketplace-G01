package com.hkust.marketplace.persistence;

import java.nio.file.Path;

/**
 * Test helper run as a separate process: opens the database file (taking its lock), prints
 * "OPEN", and keeps it open until its standard input is closed. Stands in for a first running
 * copy of the app.
 */
public final class LockHolder {

    private LockHolder() {
    }

    public static void main(String[] args) throws Exception {
        try (Database db = Database.openFile(Path.of(args[0]), false)) {
            System.out.println("OPEN");
            System.out.flush();
            while (System.in.read() != -1) {
                // wait until the parent closes our input
            }
        }
    }
}
