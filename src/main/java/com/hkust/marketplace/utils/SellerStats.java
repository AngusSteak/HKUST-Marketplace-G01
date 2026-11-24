package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.ListingStatus;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Immutable snapshot of a seller's activity, derived on demand from the stores and never
 * persisted. The dashboard renders this value; it never aggregates the stores itself.
 */
public record SellerStats(Totals totals, List<ListingStat> listings, Map<DealType, Integer> completedByType) {

    public record Totals(int views, int offers, int completed, OptionalDouble averageRating, int visibleReviews) {
    }

    public record ListingStat(String listingId, String title, ListingStatus status,
                              int views, int offers, int completed) {
    }
}
