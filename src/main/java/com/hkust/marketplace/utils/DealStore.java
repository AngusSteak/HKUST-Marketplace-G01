package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /** The buyer's requests still waiting on a response: Pending or Countered (My Deals). */
    public List<DealRequest> openRequestsByBuyer(String username) {
        return requestsByBuyer(username).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED)
                .toList();
    }

    /** The buyer's deals in progress (My Deals). */
    public List<Deal> activeDealsForBuyer(String username) {
        return dealsForBuyer(username).stream()
                .filter(d -> d.getStatus() == DealStatus.ACTIVE)
                .toList();
    }

    /** The buyer's finished deals, most recently finished first (Deal History). */
    public List<Deal> pastDealsForBuyer(String username) {
        List<Deal> past = new ArrayList<>(dealsForBuyer(username).stream()
                .filter(d -> d.getStatus() != DealStatus.ACTIVE)
                .toList());
        // Newest-created first, then a stable sort by finish time, so ties keep the newer deal on top.
        Collections.reverse(past);
        past.sort(Comparator.comparing(Deal::getCompletedDate).reversed());
        return List.copyOf(past);
    }

    private static Predicate<DealRequest> byUser(String username,
                                                 Function<DealRequest, String> field) {
        return r -> username.equalsIgnoreCase(field.apply(r));
    }

    /**
     * Seeds demo requests and deals through the lifecycle, so the statuses are consistent:
     * <ul>
     *   <li>alice: HK$160 offer on the Calculus Textbook, accepted (listing RESERVED)</li>
     *   <li>alice: pending claim on the IKEA Desk Lamp giveaway (listing RESERVED)</li>
     *   <li>bob: earlier loan of the TI-84 calculator, returned and completed (history)</li>
     *   <li>bob: new pending borrow request on the TI-84 (listing stays LIVE)</li>
     * </ul>
     */
    void seed(Buyer alice, Buyer bob, Listing textbook, Listing lamp, Listing calculator) {
        lifecycle.accept(lifecycle.submitOffer(alice, textbook, new BigDecimal("160")));

        lifecycle.submitClaim(alice, lamp);

        // Start a minute ahead: the lifecycle reads its own clock, which is later than `now`.
        LocalDateTime now = LocalDateTime.now();
        Deal pastLoan = lifecycle.accept(
                lifecycle.submitBorrow(bob, calculator, now.plusMinutes(1), now.plusDays(14)));
        lifecycle.returnItem(pastLoan);
        lifecycle.complete(pastLoan);

        lifecycle.submitBorrow(bob, calculator, now.plusDays(1), now.plusDays(30));
    }
}
