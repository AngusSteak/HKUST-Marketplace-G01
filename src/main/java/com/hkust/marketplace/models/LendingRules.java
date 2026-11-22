package com.hkust.marketplace.models;

import java.time.Duration;

/**
 * The one place the lending-duration and borrow-period rules live, so a Lend Listing and
 * the deal lifecycle cannot disagree about what a valid duration is.
 *
 * <p>The minimum is five seconds so short loans can be exercised in tests and demos
 * (SRS UC-1.4). Both methods return the value they validated, so callers assign the
 * result instead of re-reading their argument.
 */
public final class LendingRules {

    /** Shortest lending duration and shortest borrow period allowed. */
    public static final Duration MINIMUM = Duration.ofSeconds(5);

    private LendingRules() {
    }

    /**
     * Validates a Lend Listing's lending duration: present and no shorter than
     * {@link #MINIMUM}.
     *
     * @throws IllegalArgumentException if the duration is missing or too short
     */
    public static Duration requireLendingDuration(Duration lendingDuration) {
        if (lendingDuration == null) {
            throw new IllegalArgumentException("Lending duration is required");
        }
        if (lendingDuration.compareTo(MINIMUM) < 0) {
            throw new IllegalArgumentException("Lending duration must be at least "
                    + MINIMUM.toSeconds() + " seconds");
        }
        return lendingDuration;
    }

    /**
     * Validates a requested borrow period against the listing's lending duration: present,
     * no shorter than {@link #MINIMUM}, and no longer than the listing allows.
     *
     * @throws IllegalArgumentException if the period is missing, too short or too long
     */
    public static Duration requireBorrowPeriod(Duration period, Duration lendingDuration) {
        if (period == null) {
            throw new IllegalArgumentException("Borrow period is required");
        }
        if (period.compareTo(MINIMUM) < 0) {
            throw new IllegalArgumentException("Borrow period must be at least "
                    + MINIMUM.toSeconds() + " seconds");
        }
        Duration allowed = requireLendingDuration(lendingDuration);
        if (period.compareTo(allowed) > 0) {
            throw new IllegalArgumentException("Borrow period exceeds the listing's lending duration of "
                    + allowed.toSeconds() + " seconds");
        }
        return period;
    }
}
