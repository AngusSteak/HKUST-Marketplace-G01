package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
 * <p>Every transition validates first and changes nothing when it fails: invalid input
 * (a non-positive price, a bad borrow period, the wrong listing type) throws
 * {@link IllegalArgumentException}; an illegal move for the current state throws
 * {@link IllegalStateException}.
 *
 * <p>A listing carries at most one active deal. Accepting a request reserves the listing
 * (or marks it lent) and declines the other open requests on it. A giveaway claim is
 * recorded as pending and reserves the listing at once, so only the first claim succeeds
 * while the seller accepts or declines it (SRS UC-1.4 S3, UC-2.5).
 */
public final class DealLifecycle {

    private final Clock clock;
    private final Map<String, DealRequest> requests = new LinkedHashMap<>();
    private final Map<String, Deal> deals = new LinkedHashMap<>();
    /** listing id -> id of its one active deal */
    private final Map<String, String> activeDealByListing = new HashMap<>();
    /** giveaway listing id -> id of the pending claim holding its reservation */
    private final Map<String, String> pendingClaimByListing = new HashMap<>();
    private int nextRequestId = 1;
    private int nextDealId = 1;

    public DealLifecycle() {
        this(Clock.systemDefaultZone());
    }

    /** Uses {@code clock} for "now", so tests can fix the time. */
    public DealLifecycle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---------- Submit ----------

    /** Records a pending price offer on a live Sale listing. */
    public synchronized PriceOffer submitOffer(Buyer buyer, Listing listing, BigDecimal price) {
        Objects.requireNonNull(listing, "listing");
        BigDecimal money = requirePositiveMoney(price, "Offer");
        PriceOffer offer = new PriceOffer(nextRequestId(), buyer, listing, money, now());
        requireLiveAndNotDealt(listing);
        return register(offer);
    }

    /** Records a pending borrow request on a live Lend listing. */
    public synchronized BorrowRequest submitBorrow(Buyer buyer, Listing listing,
                                                   LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(listing, "listing");
        BorrowRequest request = new BorrowRequest(nextRequestId(), buyer, listing, start, end, now());
        validateBorrowPeriod(listing, start, end);
        requireLiveAndNotDealt(listing);
        return register(request);
    }

    /**
     * Records a pending claim on a live Giveaway listing and reserves it, so a second
     * claim fails until the seller declines this one. Synchronized: first claim wins.
     */
    public synchronized Claim submitClaim(Buyer buyer, Listing listing) {
        Objects.requireNonNull(listing, "listing");
        Claim claim = new Claim(nextRequestId(), buyer, listing, now());
        requireLiveAndNotDealt(listing);
        register(claim);
        listing.setStatus(ListingStatus.RESERVED);
        pendingClaimByListing.put(listing.getId(), claim.getId());
        return claim;
    }

    // ---------- Counter / accept / decline ----------

    /** Seller counters a price offer; repeatable while the offer is countered. */
    public synchronized PriceOffer counterOffer(PriceOffer offer, BigDecimal counterPrice) {
        requireOpen(offer, "counter");
        BigDecimal money = requirePositiveMoney(counterPrice, "Counter price");
        offer.setCounterPrice(money);
        offer.setStatus(RequestStatus.COUNTERED);
        return offer;
    }

    /** Seller counters a borrow request with another period; repeatable while countered. */
    public synchronized BorrowRequest counterBorrow(BorrowRequest request,
                                                    LocalDateTime start, LocalDateTime end) {
        requireOpen(request, "counter");
        validateBorrowPeriod(request.getListing(), start, end);
        request.setCounterPeriod(start, end);
        request.setStatus(RequestStatus.COUNTERED);
        return request;
    }

    /** Seller accepts a pending request. A countered request is accepted by the buyer via {@link #acceptCounter}. */
    public synchronized Deal accept(DealRequest request) {
        requireStatus(request, RequestStatus.PENDING);
        return acceptAsDeal(request);
    }

    /** Buyer accepts the seller's counter on their request. */
    public synchronized Deal acceptCounter(DealRequest request) {
        requireStatus(request, RequestStatus.COUNTERED);
        return acceptAsDeal(request);
    }

    /** Declines a pending or countered request. Declining a pending claim relists the giveaway. */
    public synchronized DealRequest decline(DealRequest request) {
        requireOpen(request, "decline");
        request.setStatus(RequestStatus.DECLINED);
        if (request instanceof Claim) {
            String listingId = request.getListing().getId();
            if (pendingClaimByListing.remove(listingId, request.getId())) {
                request.getListing().setStatus(ListingStatus.LIVE);
            }
        }
        return request;
    }

    // ---------- Return / complete / cancel ----------

    /** Buyer returns a borrowed item. Only loans are returned. */
    public synchronized Deal returnItem(Deal deal) {
        requireBorrowedAndActive(deal, "return");
        close(deal, DealStatus.RETURNED);
        releaseListing(deal);
        return deal;
    }

    /** Due date passed and the item was recorded as returned automatically. */
    public synchronized Deal autoReturn(Deal deal) {
        requireBorrowedAndActive(deal, "auto-return");
        close(deal, DealStatus.AUTO_RETURNED);
        releaseListing(deal);
        return deal;
    }

    /** Completes a sale or giveaway directly; a loan completes after it is returned. */
    public synchronized Deal complete(Deal deal) {
        Objects.requireNonNull(deal, "deal");
        boolean returnedLoan = deal.getDealType() == DealType.LENT
                && (deal.getStatus() == DealStatus.RETURNED || deal.getStatus() == DealStatus.AUTO_RETURNED);
        boolean directSale = deal.getStatus() == DealStatus.ACTIVE && deal.getDealType() != DealType.LENT;
        if (!returnedLoan && !directSale) {
            throw new IllegalStateException("Cannot complete a " + deal.getStatus() + " deal");
        }
        if (directSale) {
            String listingId = deal.getListing().getId();
            if (!deal.getId().equals(activeDealByListing.get(listingId))) {
                throw new IllegalStateException("Deal is not the listing's active deal");
            }
            deal.getListing().setStatus(deal.getDealType() == DealType.SOLD
                    ? ListingStatus.SOLD
                    : ListingStatus.GIVEN);
            activeDealByListing.remove(listingId, deal.getId());
            close(deal, DealStatus.COMPLETED);
        } else {
            // A returned loan's listing is already LIVE and its return date is kept;
            // finalising it must not touch a newer deal on the same listing.
            deal.setStatus(DealStatus.COMPLETED);
        }
        return deal;
    }

    public synchronized Deal cancel(Deal deal) {
        requireStatus(deal, DealStatus.ACTIVE);
        close(deal, DealStatus.CANCELLED);
        releaseListing(deal);
        return deal;
    }

    // ---------- Queries ----------

    /** The listing's one active deal, if any. */
    public synchronized Optional<Deal> activeDeal(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        return Optional.ofNullable(activeDealByListing.get(listing.getId())).map(deals::get);
    }

    /** Requests submitted for a listing, in submit order. */
    public synchronized List<DealRequest> requestsFor(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        List<DealRequest> matching = new ArrayList<>();
        for (DealRequest request : requests.values()) {
            if (request.getListing().getId().equals(listing.getId())) matching.add(request);
        }
        return matching;
    }

    /** Every request, in submit order. */
    public synchronized List<DealRequest> requests() {
        return Collections.unmodifiableList(new ArrayList<>(requests.values()));
    }

    /** Every deal, in the order the requests were accepted. */
    public synchronized List<Deal> deals() {
        return Collections.unmodifiableList(new ArrayList<>(deals.values()));
    }

    // ---------- Internals ----------

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextRequestId() {
        return "Q" + nextRequestId;
    }

    private <R extends DealRequest> R register(R request) {
        requests.put(request.getId(), request);
        nextRequestId++;
        return request;
    }

    private void validateBorrowPeriod(Listing listing, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Borrow start and end are required");
        }
        if (start.isBefore(now())) {
            throw new IllegalArgumentException("Borrow start cannot be in the past");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Borrow end must be after the start");
        }
        if (!(listing instanceof LendListing lend)) {
            throw new IllegalArgumentException("Not a lending listing");
        }
        LendingRules.requireBorrowPeriod(Duration.between(start, end), lend.getLendingDuration());
    }

    private static BigDecimal requirePositiveMoney(BigDecimal amount, String what) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(what + " must be a positive amount");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(what + " can have at most two decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
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

    private static void requireOpen(DealRequest request, String action) {
        Objects.requireNonNull(request, "request");
        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.COUNTERED) {
            throw new IllegalStateException("Cannot " + action + " a " + request.getStatus() + " request");
        }
    }

    private static void requireBorrowedAndActive(Deal deal, String action) {
        requireStatus(deal, DealStatus.ACTIVE);
        if (deal.getDealType() != DealType.LENT) {
            throw new IllegalStateException("Cannot " + action + " a " + deal.getDealType() + " deal");
        }
    }

    private Deal acceptAsDeal(DealRequest request) {
        Listing listing = request.getListing();
        if (request instanceof Claim) {
            if (!request.getId().equals(pendingClaimByListing.get(listing.getId()))) {
                throw new IllegalStateException("Claim no longer holds the listing's reservation");
            }
        } else {
            requireLiveAndNotDealt(listing);
        }
        request.setStatus(RequestStatus.ACCEPTED);
        for (DealRequest other : requests.values()) {
            if (other != request
                    && other.getListing().getId().equals(listing.getId())
                    && (other.getStatus() == RequestStatus.PENDING
                        || other.getStatus() == RequestStatus.COUNTERED)) {
                other.setStatus(RequestStatus.DECLINED);
            }
        }
        pendingClaimByListing.remove(listing.getId());
        return openDeal(request);
    }

    private Deal openDeal(DealRequest request) {
        Listing listing = request.getListing();
        listing.setStatus(request.dealType() == DealType.LENT ? ListingStatus.LENT : ListingStatus.RESERVED);
        Deal deal = new Deal("D" + nextDealId++, request, now());
        deals.put(deal.getId(), deal);
        activeDealByListing.put(listing.getId(), deal.getId());
        return deal;
    }

    private void close(Deal deal, DealStatus status) {
        deal.setStatus(status);
        deal.setCompletedDate(now());
    }

    private void releaseListing(Deal deal) {
        deal.getListing().setStatus(ListingStatus.LIVE);
        activeDealByListing.remove(deal.getListing().getId());
    }

    private static void requireStatus(DealRequest request, RequestStatus expected) {
        Objects.requireNonNull(request, "request");
        if (request.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected a " + expected + " request but was " + request.getStatus());
        }
    }

    private static void requireStatus(Deal deal, DealStatus expected) {
        Objects.requireNonNull(deal, "deal");
        if (deal.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected a " + expected + " deal but was " + deal.getStatus());
        }
    }
}
