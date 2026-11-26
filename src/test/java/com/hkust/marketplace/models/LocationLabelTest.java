package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationLabelTest {

    private static final List<String> CANONICAL_LABELS = List.of(
            "Academic Building", "LSK", "Library", "Student Centre", "North Gate", "South Gate",
            "Shaw Auditorium", "McDonald's (LG5)", "LG7 Fusion", "Sports Hall",
            "UG Hall I", "UG Hall II", "UG Hall III", "UG Hall IV",
            "UG Hall V", "UG Hall VI", "UG Hall VII", "UG Hall VIII",
            "PG Hall", "Off-campus");

    @Test
    void hasExactlyThe20CanonicalConstants() {
        assertEquals(20, Location.values().length);
        assertEquals(CANONICAL_LABELS, Arrays.stream(Location.values()).map(Location::getLabel).toList());
    }

    @Test
    void labelsAreUnique() {
        Set<String> labels = new HashSet<>();
        for (Location location : Location.values()) {
            assertTrue(labels.add(location.getLabel()), "duplicate label: " + location.getLabel());
        }
        assertEquals(Location.values().length, labels.size());
    }

    @Test
    void toStringRendersTheLabel() {
        for (Location location : Location.values()) {
            assertEquals(location.getLabel(), location.toString());
        }
    }

    @Test
    void everyLabelRoundTrips() {
        for (Location location : Location.values()) {
            assertSame(location, Location.fromLabel(location.getLabel()));
            assertSame(location, Location.fromLabel(location.toString()));
        }
    }

    @Test
    void rejectsLegacyAndUnknownLabels() {
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel("Hall 5 Lobby"));
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel("Coffee Shop (LG5)"));
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel("Academic Building (LG1)"));
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel("UG Hall 5"));
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel(""));
        assertThrows(IllegalArgumentException.class, () -> Location.fromLabel(null));
    }
}
