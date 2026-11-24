package com.hkust.marketplace.models;

/** The item condition choices offered on a listing (SRS Appendix A). */
public enum Condition {
    NEW("New"),
    LIKE_NEW("Like New"),
    USED("Used"),
    FOR_PARTS("For Parts");

    private final String label;

    Condition(String label) {
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
     * Returns the condition with this display label.
     *
     * @throws IllegalArgumentException if no condition has this label
     */
    public static Condition fromLabel(String label) {
        for (Condition condition : values()) {
            if (condition.label.equals(label)) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown condition label: " + label);
    }
}
