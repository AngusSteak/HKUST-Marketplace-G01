package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Deal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deal History filters (SRS UC-1.6 S1): the buyer's finished deals narrowed by a date range,
 * category, seller and keyword. Kept free of JavaFX so the rules are unit tested.
 */
public final class DealHistorySearch {

    /** SRS UC-1.6 A4. */
    public static final String INVALID_DATE_RANGE = "Invalid date range: the start date is after the end date.";

    private DealHistorySearch() {
    }

    /**
     * What the buyer filtered by; every field is optional ({@code null} or blank means "any").
     * The dates are inclusive and match the day the deal finished.
     *
     * @param from the first day of the range, or {@code null}
     * @param to the last day of the range, or {@code null}
     * @param category the category to keep, or {@code null}
     * @param seller text to find in the seller's name or username
     * @param keyword text to find in the listing's title or description
     */
    public record Criteria(LocalDate from, LocalDate to, Category category, String seller, String keyword) {

        /** No filters: every finished deal, in the order given. */
        public static Criteria all() {
            return new Criteria(null, null, null, null, null);
        }
    }

    /**
     * The deals matching every criterion, keeping the given order (newest first from
     * {@link DealStore#pastDealsForBuyer}).
     *
     * @throws IllegalArgumentException with {@link #INVALID_DATE_RANGE} if {@code from} is after {@code to}
     */
    public static List<Deal> filter(Collection<Deal> deals, Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new IllegalArgumentException(INVALID_DATE_RANGE);
        }
        String seller = normalise(criteria.seller());
        String keyword = normalise(criteria.keyword());
        return deals.stream()
                .filter(d -> d.getCompletedDate() != null)
                .filter(d -> criteria.from() == null || !d.getCompletedDate().toLocalDate().isBefore(criteria.from()))
                .filter(d -> criteria.to() == null || !d.getCompletedDate().toLocalDate().isAfter(criteria.to()))
                .filter(d -> criteria.category() == null || d.getListing().getCategory() == criteria.category())
                .filter(d -> seller.isEmpty() || contains(d.getSellerDisplayName(), seller)
                        || contains(d.getSellerUsername(), seller))
                .filter(d -> keyword.isEmpty() || contains(d.getListing().getTitle(), keyword)
                        || contains(d.getListing().getDescription(), keyword))
                .toList();
    }

    private static boolean contains(String text, String needle) {
        return text != null && normalise(text).contains(needle);
    }

    private static String normalise(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
