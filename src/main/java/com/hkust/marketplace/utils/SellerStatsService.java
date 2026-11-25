package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives a seller's activity figures on demand from the stores. Read-only: it never writes to a
 * store and never caches a result. The visibility rule lives in {@link ListingStore#bySeller}, so
 * hidden listings are excluded once, here, and cannot leak into any total or row.
 */
public final class SellerStatsService {

    private final DummyDataService data;

    /** Creates the service over the given stores. */
    public SellerStatsService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** The seller's stats, or a security exception if the actor is not an active seller. */
    public SellerStats statsFor(User actor) {
        AccessControl.require(actor, UserRole.SELLER);
        String username = actor.getUsername();

        List<Listing> visible = data.listings().bySeller(username);
        Set<String> ids = visible.stream().map(Listing::getId).collect(Collectors.toSet());
        List<DealRequest> requests = data.deals().allRequests().stream()
                .filter(request -> ids.contains(request.getListing().getId()))
                .toList();
        List<Deal> deals = data.deals().allDeals().stream()
                .filter(deal -> ids.contains(deal.getListing().getId()))
                .toList();

        Map<DealType, Integer> byType = new EnumMap<>(DealType.class);
        for (DealType type : DealType.values()) {
            byType.put(type, 0);
        }
        int completed = 0;
        for (Deal deal : deals) {
            if (isCompleted(deal.getStatus())) {
                completed++;
                byType.merge(deal.getDealType(), 1, Integer::sum);
            }
        }

        List<SellerStats.ListingStat> rows = visible.stream()
                .map(listing -> new SellerStats.ListingStat(
                        listing.getId(),
                        listing.getTitle(),
                        listing.getStatus(),
                        listing.getViewCount(),
                        countRequestsFor(requests, listing.getId()),
                        countCompletedDealsFor(deals, listing.getId()),
                        completedDealTypeFor(deals, listing.getId())))
                .toList();

        int views = visible.stream().mapToInt(Listing::getViewCount).sum();
        SellerStats.Totals totals = new SellerStats.Totals(
                views,
                requests.size(),
                completed,
                data.reviews().averageRating(username),
                data.reviews().visibleCount(username));

        return new SellerStats(totals, List.copyOf(rows), Collections.unmodifiableMap(byType));
    }

    private static boolean isCompleted(DealStatus status) {
        return status == DealStatus.COMPLETED
                || status == DealStatus.RETURNED
                || status == DealStatus.AUTO_RETURNED;
    }

    private static int countRequestsFor(List<DealRequest> requests, String listingId) {
        return (int) requests.stream()
                .filter(request -> request.getListing().getId().equals(listingId))
                .count();
    }

    private static int countCompletedDealsFor(List<Deal> deals, String listingId) {
        return (int) deals.stream()
                .filter(deal -> deal.getListing().getId().equals(listingId) && isCompleted(deal.getStatus()))
                .count();
    }

    private static DealType completedDealTypeFor(List<Deal> deals, String listingId) {
        return deals.stream()
                .filter(deal -> deal.getListing().getId().equals(listingId) && isCompleted(deal.getStatus()))
                .map(Deal::getDealType)
                .findFirst()
                .orElse(null);
    }
}
