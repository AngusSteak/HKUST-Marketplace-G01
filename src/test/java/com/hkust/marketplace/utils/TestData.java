package com.hkust.marketplace.utils;

import com.hkust.marketplace.persistence.Database;

/** Gives screen tests (in other packages) the demo data on a private in-memory database. */
public final class TestData {

    private TestData() {
    }

    /** Seeds a fresh in-memory database and makes it the shared instance the screens use. */
    public static DummyDataService installSeeded() {
        DummyDataService data = new DummyDataService(Database.inMemory());
        data.seedData();
        DummyDataService.replaceInstanceForTests(data);
        return data;
    }

    /** Forgets the test instance. */
    public static void uninstall() {
        DummyDataService.replaceInstanceForTests(null);
    }
}
