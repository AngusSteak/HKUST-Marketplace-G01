package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Filter predicates for the admin deals table and review list (SRS UC-3.5, UC-3.6). Kept out
 * of the JavaFX controller so the exact matching rules can be tested without a toolkit.
 */
public final class AdminFilters {

    /** SRS UC-3.5 A1/A2: shown instead of an empty table when the range starts after it ends. */
    public static final String INVALID_DEAL_DATE_RANGE =
            "The date range is invalid: the start date is after the end date.";

    private AdminFilters() {
    }

    /**
     * Matches the review list filters (SRS UC-3.6): keyword over seller, buyer and comment, then
     * a star score and a visibility state.
     *
     * @param review the review to test
     * @param keyword text to find in the seller, buyer or comment; blank means any
     * @param scoreIndex 0 for "All scores", otherwise 1 for 5 stars down to 5 for 1 star
     * @param visibility "All reviews", "Visible", "Hidden" or "Flagged"
     * @return {@code true} when the review passes every filter
     */
    public static boolean matchesReview(Review review, String keyword, int scoreIndex, String visibility) {
        String text = normalise(keyword);
        if (!text.isEmpty()
                && !contains(review.getSellerUsername(), text)
                && !contains(review.getBuyerUsername(), text)
                && !contains(review.getComment(), text)) {
            return false;
        }
        int wanted = scoreIndex == 0 ? 0 : 6 - scoreIndex;
        if (wanted != 0 && review.getRating() != wanted) {
            return false;
        }
        if ("Hidden".equals(visibility)) {
            return review.isHidden();
        }
        if ("Visible".equals(visibility)) {
            return !review.isHidden();
        }
        if ("Flagged".equals(visibility)) {
            return review.isFlagged() && !review.isHidden();
        }
        return true;
    }

    /**
     * Matches the deals table filters (SRS UC-3.5): keyword over title and both usernames, deal
     * type, status, and a date range that matches either the start or the end date.
     *
     * @param deal the deal to test
     * @param keyword text to find in the title or usernames; blank means any
     * @param type the exact deal type, or "All types"/{@code null}
     * @param status the exact deal status, or "All statuses"/{@code null}
     * @param from the first day of the range, or {@code null}
     * @param to the last day of the range, or {@code null}
     * @return {@code true} when the deal passes every filter
     */
    public static boolean matchesDeal(Deal deal, String keyword, String type, String status,
                                      LocalDate from, LocalDate to) {
        String text = normalise(keyword);
        if (!text.isEmpty()
                && !contains(deal.getListing().getTitle(), text)
                && !contains(deal.getBuyerUsername(), text)
                && !contains(deal.getSellerUsername(), text)) {
            return false;
        }
        if (type != null && !type.equals("All types") && !type.equals(deal.getDealType().name())) {
            return false;
        }
        if (status != null && !status.equals("All statuses") && !status.equals(deal.getStatus().name())) {
            return false;
        }
        if (from == null && to == null) {
            return true;
        }
        LocalDate start = deal.getStartDate().toLocalDate();
        LocalDate end = deal.getCompletedDate() != null ? deal.getCompletedDate().toLocalDate()
                : deal.getDueDate() != null ? deal.getDueDate().toLocalDate() : null;
        return inRange(start, from, to) || (end != null && inRange(end, from, to));
    }

    /**
     * A historical record labels a soft-deleted account "deleted user" (SRS UC-3.3).
     *
     * @param user the account, or {@code null} when it no longer exists
     * @param username the username to fall back to
     * @return the label shown in historical tables
     */
    public static String partyLabel(User user, String username) {
        if (user == null) {
            return username;
        }
        return user.isDeleted() ? "deleted user" : user.getFullName() + " (@" + username + ")";
    }

    /**
     * Whether a deal date range is usable (SRS UC-3.5 A1/A2). An open-ended range is always
     * valid; a range is invalid only when both ends are given and the start is after the end.
     *
     * @param from the first day of the range, or {@code null}
     * @param to the last day of the range, or {@code null}
     * @return {@code true} when either end is missing or {@code from} is not after {@code to}
     */
    public static boolean isValidDealDateRange(LocalDate from, LocalDate to) {
        return from == null || to == null || !from.isAfter(to);
    }

    static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String normalise(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
