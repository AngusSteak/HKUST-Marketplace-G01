package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single source of truth for request, deal and listing status transitions, so the
 * buyer, seller and admin portals all agree on what is allowed.
 *
 * <p>Every transition checks the current state first and throws
 * {@link IllegalStateException} on an illegal move, without changing any state.
 * A listing can carry at most one active deal; accepting a request reserves the
 * listing (or marks it lent) and declines the other pending requests.
 */
public final class DealLifecycle {

    /** A buyer's request for a listing, e.g. an offer, borrow request or giveaway claim. */
    public static final class Request {
        private final String id;
        private final Listing listing;
        private final DealType type;
        private RequestStatus status;

        private Request(String id, Listing listing, DealType type) {
            this.id = id;
            this.listing = listing;
            this.type = type;
            this.status = RequestStatus.PENDING;
        }

        public String getId() { return id; }
        public Listing getListing() { return listing; }
        public DealType getType() { return type; }
        public RequestStatus getStatus() { return status; }
    }

    /** An accepted request and its own lifecycle. */
    public static final class DealRecord {
        private final String id;
        private final Request request;
        private DealStatus status;

        private DealRecord(String id, Request request) {
            this.id = id;
            this.request = request;
            this.status = DealStatus.ACTIVE;
        }

        public String getId() { return id; }
        public Request getRequest() { return request; }
        public DealStatus getStatus() { return status; }
    }

    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<String, DealRecord> deals = new LinkedHashMap<>();
    /** listing id -> id of its one active deal */
    private final Map<String, String> activeDealByListing = new HashMap<>();
    private int nextRequestId = 1;
    private int nextDealId = 1;

    /**
     * Submits a request for a live, for-sale or for-lending listing. Giveaways go
     * through {@link #claim(Listing)} because a claim must be accepted atomically.
     */
    public Request submit(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        if (listing.getType() == ListingType.GIVEAWAY) {
            throw new IllegalStateException("Giveaway listings are claimed, not offered");
        }
        requireLiveAndNotDealt(listing);
        Request request = new Request("Q" + nextRequestId++, listing, dealTypeFor(listing));
        requests.put(request.id, request);
        return request;
    }

    /**
     * First claim wins: the request is accepted and the giveaway is reserved in one
     * step. A second claim on the same listing throws.
     */
    public Request claim(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        if (listing.getType() != ListingType.GIVEAWAY) {
            throw new IllegalStateException("Only giveaway listings can be claimed");
        }
        requireLiveAndNotDealt(listing);
        Request request = new Request("Q" + nextRequestId++, listing, DealType.GIVEN);
        request.status = RequestStatus.ACCEPTED;
        requests.put(request.id, request);
        openDeal(request);
        return request;
    }

    public Request counter(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.status != RequestStatus.PENDING && request.status != RequestStatus.COUNTERED) {
            throw new IllegalStateException("Cannot counter a " + request.status + " request");
        }
        request.status = RequestStatus.COUNTERED;
        return request;
    }

    /** Accepts a pending request. A countered request must use {@link #acceptCounter}. */
    public DealRecord accept(Request request) {
        requireStatus(request, RequestStatus.PENDING);
        return acceptAsDeal(request);
    }

    public DealRecord acceptCounter(Request request) {
        requireStatus(request, RequestStatus.COUNTERED);
        return acceptAsDeal(request);
    }

    public Request decline(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.status != RequestStatus.PENDING && request.status != RequestStatus.COUNTERED) {
            throw new IllegalStateException("Cannot decline a " + request.status + " request");
        }
        request.status = RequestStatus.DECLINED;
        return request;
    }

    /** Buyer returns a borrowed item. Only loans are returned. */
    public DealRecord returnItem(DealRecord deal) {
        requireBorrowedAndActive(deal, "return");
        deal.status = DealStatus.RETURNED;
        releaseListing(deal);
        return deal;
    }

    /** Due date passed and the item was recorded as returned automatically. */
    public DealRecord autoReturn(DealRecord deal) {
        requireBorrowedAndActive(deal, "auto-return");
        deal.status = DealStatus.AUTO_RETURNED;
        releaseListing(deal);
        return deal;
    }

    /** Completes a sale or giveaway directly; a loan completes after it is returned. */
    public DealRecord complete(DealRecord deal) {
        Objects.requireNonNull(deal, "deal");
        boolean returnedLoan = deal.request.type == DealType.LENT
                && (deal.status == DealStatus.RETURNED || deal.status == DealStatus.AUTO_RETURNED);
        boolean directSale = deal.status == DealStatus.ACTIVE && deal.request.type != DealType.LENT;
        if (!returnedLoan && !directSale) {
            throw new IllegalStateException("Cannot complete a " + deal.status + " deal");
        }
        String listingId = deal.request.listing.getId();
        if (directSale) {
            if (!deal.getId().equals(activeDealByListing.get(listingId))) {
                throw new IllegalStateException("Deal is not the listing's active deal");
            }
            deal.request.listing.setStatus(deal.request.type == DealType.SOLD
                    ? ListingStatus.SOLD
                    : ListingStatus.GIVEN);
            activeDealByListing.remove(listingId, deal.getId());
        }
        // A returned loan's listing is already LIVE; finalising it must not touch a newer deal.
        deal.status = DealStatus.COMPLETED;
        return deal;
    }

    public DealRecord cancel(DealRecord deal) {
        requireStatus(deal, DealStatus.ACTIVE);
        deal.status = DealStatus.CANCELLED;
        releaseListing(deal);
        return deal;
    }

    /** The listing's one active deal, if any. */
    public Optional<DealRecord> activeDeal(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        return Optional.ofNullable(activeDealByListing.get(listing.getId())).map(deals::get);
    }

    /** Requests submitted for a listing, in submit order. */
    public List<Request> requestsFor(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        List<Request> matching = new ArrayList<>();
        for (Request request : requests.values()) {
            if (request.listing.getId().equals(listing.getId())) matching.add(request);
        }
        return matching;
    }

    private void requireLiveAndNotDealt(Listing listing) {
        Objects.requireNonNull(listing.getId(), "listing id");
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException("Listing is not live: " + listing.getStatus());
        }
        if (activeDealByListing.containsKey(listing.getId())) {
            throw new IllegalStateException("Listing already has an active deal");
        }
    }

    private static void requireBorrowedAndActive(DealRecord deal, String action) {
        requireStatus(deal, DealStatus.ACTIVE);
        if (deal.request.type != DealType.LENT) {
            throw new IllegalStateException("Cannot " + action + " a " + deal.request.type + " deal");
        }
    }

    private DealRecord acceptAsDeal(Request request) {
        requireLiveAndNotDealt(request.listing);
        request.status = RequestStatus.ACCEPTED;
        for (Request other : requests.values()) {
            if (other.listing.getId().equals(request.listing.getId())
                    && (other.status == RequestStatus.PENDING
                        || other.status == RequestStatus.COUNTERED)) {
                other.status = RequestStatus.DECLINED;
            }
        }
        return openDeal(request);
    }

    private DealRecord openDeal(Request request) {
        request.listing.setStatus(request.type == DealType.LENT
                ? ListingStatus.LENT
                : ListingStatus.RESERVED);
        DealRecord deal = new DealRecord("D" + nextDealId++, request);
        deals.put(deal.id, deal);
        activeDealByListing.put(request.listing.getId(), deal.id);
        return deal;
    }

    private void releaseListing(DealRecord deal) {
        deal.request.listing.setStatus(ListingStatus.LIVE);
        activeDealByListing.remove(deal.request.listing.getId());
    }

    private static void requireStatus(Request request, RequestStatus expected) {
        Objects.requireNonNull(request, "request");
        if (request.status != expected) {
            throw new IllegalStateException(
                    "Expected a " + expected + " request but was " + request.status);
        }
    }

    private static void requireStatus(DealRecord deal, DealStatus expected) {
        Objects.requireNonNull(deal, "deal");
        if (deal.status != expected) {
            throw new IllegalStateException(
                    "Expected a " + expected + " deal but was " + deal.status);
        }
    }

    private static DealType dealTypeFor(Listing listing) {
        return switch (listing.getType()) {
            case SELL -> DealType.SOLD;
            case LEND -> DealType.LENT;
            case GIVEAWAY -> DealType.GIVEN;
        };
    }
}
