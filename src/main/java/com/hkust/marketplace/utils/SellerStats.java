package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.ListingStatus;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Immutable snapshot of a seller's activity, derived on demand from the stores and never
 * persisted. The dashboard renders this value; it never aggregates the stores itself.
 *
 * @param totals the headline counts
 * @param listings one row per listing owned by the seller
 * @param completedByType completed deals counted per deal type
 */
public record SellerStats(Totals totals, List<ListingStat> listings, Map<DealType, Integer> completedByType) {

    /**
     * The headline numbers on the stats page.
     *
     * @param views total views across the seller's listings
     * @param offers total offers received
     * @param completed total completed deals
     * @param averageRating the average visible review score, empty when there are no reviews
     * @param visibleReviews how many reviews are visible
     */
    public record Totals(int views, int offers, int completed, OptionalDouble averageRating, int visibleReviews) {
    }

    /**
     * One listing's activity.
     *
     * @param listingId the listing id
     * @param title the listing title
     * @param status the current listing status
     * @param views views of this listing
     * @param offers offers received on this listing
     * @param completed completed deals for this listing
     */
    public record ListingStat(String listingId, String title, ListingStatus status,
                              int views, int offers, int completed) {
    }
}
