package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * In-memory deal requests and deals. All state changes go through {@link #lifecycle()};
 * this store only answers queries for the portals.
 */
public class DealStore {

    private final DealLifecycle lifecycle = new DealLifecycle();

    /** The transition engine; use it to submit, counter, accept, return or complete. */
    public DealLifecycle lifecycle() {
        return lifecycle;
    }

    public List<DealRequest> allRequests() {
        return lifecycle.requests();
    }

    public List<Deal> allDeals() {
        return lifecycle.deals();
    }

    /** Every request the buyer has made, in submit order. */
    public List<DealRequest> requestsByBuyer(String username) {
        return lifecycle.requests().stream()
                .filter(byUser(username, DealRequest::getBuyerUsername))
                .toList();
    }

    /** Every request made on the seller's listings, in submit order. */
    public List<DealRequest> requestsForSeller(String username) {
        return lifecycle.requests().stream()
                .filter(byUser(username, DealRequest::getSellerUsername))
                .toList();
    }

    public List<Deal> dealsForBuyer(String username) {
        return lifecycle.deals().stream()
                .filter(d -> username.equalsIgnoreCase(d.getBuyerUsername()))
                .toList();
    }

    public List<Deal> dealsForSeller(String username) {
        return lifecycle.deals().stream()
                .filter(d -> username.equalsIgnoreCase(d.getSellerUsername()))
                .toList();
    }

    private static Predicate<DealRequest> byUser(String username,
                                                 Function<DealRequest, String> field) {
        return r -> username.equalsIgnoreCase(field.apply(r));
    }

    /** Seeds alice's accepted HK$160 offer on the given Sale listing. */
    void seed(Buyer alice, Listing saleListing) {
        DealRequest offer = lifecycle.submitOffer(alice, saleListing, new BigDecimal("160"));
        lifecycle.accept(offer);
    }
}
