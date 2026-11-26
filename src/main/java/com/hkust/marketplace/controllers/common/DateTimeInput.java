package com.hkust.marketplace.controllers.common;

import javafx.geometry.Pos;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/**
 * A date picker and a time box side by side, used wherever a borrow period is entered (the
 * buyer's borrow request and the seller's counter-period). Seconds are optional so the shortest
 * allowed borrow period (5 seconds) can be entered.
 */
public final class DateTimeInput {

    private static final DateTimeFormatter TIME_DISPLAY = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_INPUT = new DateTimeFormatterBuilder()
            .appendPattern("H:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .toFormatter();

    private final DatePicker date;
    private final TextField time;
    private final HBox node;

    /** Starts at {@code initial}, or empty if it is {@code null}. */
    public DateTimeInput(LocalDateTime initial) {
        date = new DatePicker(initial == null ? null : initial.toLocalDate());
        date.setPrefWidth(150);
        time = new TextField(initial == null ? "" : initial.toLocalTime().format(TIME_DISPLAY));
        time.setPromptText("HH:mm:ss");
        time.setPrefWidth(90);
        node = new HBox(8, date, time);
        node.setAlignment(Pos.CENTER_LEFT);
    }

    /** The picker and the time box, to add to a layout. */
    public HBox node() {
        return node;
    }

    /**
     * The entered date and time.
     *
     * @param which "start" or "end", used in the error message
     * @throws IllegalArgumentException with a message for the user if the date is missing or the
     *         time is not {@code H:mm} or {@code H:mm:ss}
     */
    public LocalDateTime value(String which) {
        return parse(date.getValue(), time.getText(), which);
    }

    /** Combines a date and an {@code H:mm} or {@code H:mm:ss} time, keeping the seconds. */
    public static LocalDateTime parse(LocalDate date, String timeText, String which) {
        if (date == null) {
            throw new IllegalArgumentException("Please choose a " + which + " date.");
        }
        try {
            return date.atTime(LocalTime.parse(timeText == null ? "" : timeText.trim(), TIME_INPUT));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Enter the " + which + " time as HH:mm:ss, e.g. 14:30:00.");
        }
    }
}
