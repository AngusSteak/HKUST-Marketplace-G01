package com.hkust.marketplace.models;

/** The fourteen SRS categories used to group and filter listings (SRS Appendix A). */
public enum Category {
    BOOKS_AND_NOTES("Books & Notes"),
    ELECTRONICS("Electronics"),
    FURNITURE("Furniture"),
    CLOTHING("Clothing"),
    SPORTS_AND_OUTDOORS("Sports & Outdoors"),
    KITCHEN_AND_HOME("Kitchen & Home"),
    STATIONERY("Stationery"),
    OTHERS("Others");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /**
     * Returns the category with this display label.
     *
     * @throws IllegalArgumentException if no category has this label
     */
    public static Category fromLabel(String label) {
        for (Category category : values()) {
            if (category.label.equals(label)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category label: " + label);
    }
}
