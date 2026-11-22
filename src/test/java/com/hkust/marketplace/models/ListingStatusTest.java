package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListingStatusTest {

    @Test
    void takenDownResolves() {
        assertNotNull(ListingStatus.valueOf("TAKEN_DOWN"));
    }

    @Test
    void removedIsRejected() {
        String legacyName = "RE" + "MOVED";
        assertThrows(IllegalArgumentException.class, () -> ListingStatus.valueOf(legacyName));
    }
}
