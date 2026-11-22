package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionLabelTest {

    @Test
    void hasExactlyFourConstantsWithExactLabels() {
        assertEquals(4, Condition.values().length);
        assertEquals("New", Condition.NEW.getLabel());
        assertEquals("Like New", Condition.LIKE_NEW.getLabel());
        assertEquals("Used", Condition.USED.getLabel());
        assertEquals("For Parts", Condition.FOR_PARTS.getLabel());
    }

    @Test
    void toStringReturnsLabel() {
        for (Condition condition : Condition.values()) {
            assertEquals(condition.getLabel(), condition.toString());
        }
    }

    @Test
    void everyLabelRoundTrips() {
        for (Condition condition : Condition.values()) {
            assertSame(condition, Condition.fromLabel(condition.getLabel()));
        }
    }

    @Test
    void unknownLabelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Condition.fromLabel("Mint"));
        assertThrows(IllegalArgumentException.class, () -> Condition.fromLabel(null));
    }
}
