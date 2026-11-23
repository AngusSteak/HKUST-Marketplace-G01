package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Browse Marketplace rules (SRS UC-1.3): which listings a buyer sees, and the search,
 * filter and sort over them. Kept free of JavaFX so the rules are unit tested; the screen only
 * collects the criteria and shows the result.
 */
public final class ListingSearch {

    /** SRS UC-1.3 A2. */
    public static final String INVALID_PRICE_RANGE =
            "Invalid price range: prices can't be negative and the minimum can't exceed the maximum.";

    private ListingSearch() {
    }

    /** The three sort orders of SRS UC-1.3 step 3.3. */
    public enum SortOrder {
        NEWEST("Newest"),
        PRICE_LOW_HIGH("Price: low to high"),
        PRICE_HIGH_LOW("Price: high to low");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }

        @Override
        public String toString() { return label; }
    }

    /**
     * What the buyer asked for. Every field except {@code sort} is optional: {@code null} (or a
     * blank keyword) means "any".
     */
    public record Criteria(String keyword, Category category, ListingType type, Condition condition,
                           BigDecimal minPrice, BigDecimal maxPrice, SortOrder sort) {

        public Criteria {
            Objects.requireNonNull(sort, "sort");
        }

        /** No search or filters, newest first: the screen's initial state. */
        public static Criteria all() {
            return new Criteria(null, null, null, null, null, null, SortOrder.NEWEST);
        }

        boolean hasPriceRange() {
            return minPrice != null || maxPrice != null;
        }
    }

    /**
     * The live listings matching every criterion, in the requested order.
     *
     * <p>Only Sale listings have a price, so a price range keeps Sale listings only. Sorting by
     * price puts listings without a price (lend, giveaway) after the priced ones; ties are newest
     * first.
     *
     * @throws IllegalArgumentException with {@link #INVALID_PRICE_RANGE} if a price is negative
     *         or the minimum is above the maximum (SRS UC-1.3 A2)
     */
    public static List<Listing> search(Collection<Listing> listings, Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        requireValidPriceRange(criteria.minPrice(), criteria.maxPrice());
        String keyword = criteria.keyword() == null ? "" : criteria.keyword().trim().toLowerCase(Locale.ROOT);
        return listings.stream()
                .filter(ListingSearch::isBrowsable)
                .filter(l -> keyword.isEmpty() || contains(l.getTitle(), keyword) || contains(l.getDescription(), keyword))
                .filter(l -> criteria.category() == null || l.getCategory() == criteria.category())
                .filter(l -> criteria.type() == null || l.getType() == criteria.type())
                .filter(l -> criteria.condition() == null || l.getCondition() == criteria.condition())
                .filter(l -> !criteria.hasPriceRange() || inRange(price(l), criteria.minPrice(), criteria.maxPrice()))
                .sorted(comparator(criteria.sort()))
                .toList();
    }

    /**
     * A buyer sees a listing only while it is Live and its seller's account is active: a disabled
     * seller's listings are hidden by account status without changing the listings themselves
     * (SRS UC-3.4), and a deleted seller's are hidden too.
     */
    public static boolean isBrowsable(Listing listing) {
        Seller seller = listing.getSeller();
        return listing.getStatus() == ListingStatus.LIVE && seller.isActive() && !seller.isDeleted();
    }

    /**
     * When the listing went live: the admin approval time, or the submission time for a listing
     * that went live without a review record (seeded demo data).
     */
    public static LocalDateTime postedDate(Listing listing) {
        return listing.getReviewedAt() != null ? listing.getReviewedAt() : listing.getSubmittedDate();
    }

    /** @throws IllegalArgumentException with {@link #INVALID_PRICE_RANGE} if the range is invalid */
    public static void requireValidPriceRange(BigDecimal min, BigDecimal max) {
        boolean negative = (min != null && min.signum() < 0) || (max != null && max.signum() < 0);
        boolean reversed = min != null && max != null && min.compareTo(max) > 0;
        if (negative || reversed) {
            throw new IllegalArgumentException(INVALID_PRICE_RANGE);
        }
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /** The sale price, or {@code null} for a listing without one. */
    private static BigDecimal price(Listing listing) {
        return listing instanceof SaleListing sale ? sale.getPrice().amount() : null;
    }

    private static boolean inRange(BigDecimal price, BigDecimal min, BigDecimal max) {
        return price != null
                && (min == null || price.compareTo(min) >= 0)
                && (max == null || price.compareTo(max) <= 0);
    }

    private static Comparator<Listing> comparator(SortOrder sort) {
        Comparator<Listing> newest = Comparator.comparing(ListingSearch::postedDate).reversed();
        return switch (sort) {
            case NEWEST -> newest;
            case PRICE_LOW_HIGH -> Comparator.comparing(ListingSearch::price,
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder())).thenComparing(newest);
            case PRICE_HIGH_LOW -> Comparator.comparing(ListingSearch::price,
                    Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())).thenComparing(newest);
        };
    }
}
