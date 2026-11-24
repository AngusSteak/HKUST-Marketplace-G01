package com.hkust.marketplace.utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders a {@link Duration} for people: the largest two non-zero units, largest first.
 *
 * <p>Asking a duration for whole days hides short values ("5 seconds" became "0 days") and
 * drops hours ("5 days 12 hours" became "5 days"), so screens read durations through here
 * instead.
 */
public final class DurationText {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final long SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
    private static final int MAX_UNITS = 2;

    private DurationText() {
    }

    /**
     * Formats a duration as a short phrase.
     *
     * @param duration the duration to describe; must not be null
     * @return a phrase such as "5 seconds", "2 hours 30 minutes" or "90 days"; a zero or
     *         sub-second duration reads as "0 seconds"
     */
    public static String format(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long totalSeconds = Math.max(0, duration.getSeconds());

        long[] amounts = {
                totalSeconds / SECONDS_PER_DAY,
                (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR,
                (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
                totalSeconds % SECONDS_PER_MINUTE
        };
        String[] names = {"day", "hour", "minute", "second"};

        List<String> parts = new ArrayList<>(MAX_UNITS);
        for (int i = 0; i < amounts.length && parts.size() < MAX_UNITS; i++) {
            if (amounts[i] > 0) {
                parts.add(amounts[i] + " " + names[i] + (amounts[i] == 1 ? "" : "s"));
            }
        }
        return parts.isEmpty() ? "0 seconds" : String.join(" ", parts);
    }
}
