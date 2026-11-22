package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryLabelTest {

    @Test
    void hasExactlyEightCategories() {
        assertEquals(8, Category.values().length);
    }

    @Test
    void labelsAreExact() {
        assertEquals("Books & Notes", Category.BOOKS_AND_NOTES.getLabel());
        assertEquals("Electronics", Category.ELECTRONICS.getLabel());
        assertEquals("Furniture", Category.FURNITURE.getLabel());
        assertEquals("Clothing", Category.CLOTHING.getLabel());
        assertEquals("Sports & Outdoors", Category.SPORTS_AND_OUTDOORS.getLabel());
        assertEquals("Kitchen & Home", Category.KITCHEN_AND_HOME.getLabel());
        assertEquals("Stationery", Category.STATIONERY.getLabel());
        assertEquals("Others", Category.OTHERS.getLabel());
    }

    @Test
    void toStringRendersTheLabel() {
        for (Category category : Category.values()) {
            assertEquals(category.getLabel(), category.toString());
        }
    }

    @Test
    void everyLabelRoundTrips() {
        for (Category category : Category.values()) {
            assertSame(category, Category.fromLabel(category.getLabel()));
        }
    }

    @Test
    void unknownLabelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Category.fromLabel("Textbooks"));
        assertThrows(IllegalArgumentException.class, () -> Category.fromLabel(null));
    }
}
