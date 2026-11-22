package com.hkust.marketplace.models;

public enum Location {
    ACADEMIC_BUILDING("Academic Building"),
    LIBRARY("Library"),
    STUDENT_CENTRE("Student Centre"),
    NORTH_GATE("North Gate"),
    SOUTH_GATE("South Gate"),
    SHAW_AUDITORIUM("Shaw Auditorium"),
    COFFEE_SHOP_LG5("Coffee Shop (LG5)"),
    SPORTS_HALL("Sports Hall"),
    UG_HALL_I("UG Hall I"),
    UG_HALL_II("UG Hall II"),
    UG_HALL_III("UG Hall III"),
    UG_HALL_IV("UG Hall IV"),
    UG_HALL_V("UG Hall V"),
    UG_HALL_VI("UG Hall VI"),
    UG_HALL_VII("UG Hall VII"),
    UG_HALL_VIII("UG Hall VIII"),
    PG_HALL("PG Hall"),
    OFF_CAMPUS("Off-campus");

    private final String label;

    Location(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static Location fromLabel(String label) {
        for (Location location : values()) {
            if (location.label.equals(label)) {
                return location;
            }
        }
        throw new IllegalArgumentException("Unknown location label: " + label);
    }
}
