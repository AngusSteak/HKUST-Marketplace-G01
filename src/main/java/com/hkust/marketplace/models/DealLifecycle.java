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
    private final Map<String, CounterOffer> counters = new LinkedHashMap<>();
    /** listing id -> id of its one active deal */
    private final Map<String, String> activeDealByListing = new HashMap<>();
    /** giveaway listing id -> id of the pending claim holding its reservation */
    private final Map<String, String> pendingClaimByListing = new HashMap<>();
    /** buyer request id -> id of the one unanswered counter sent on it */
    private final Map<String, String> activeCounterByRequest = new HashMap<>();
    private int nextRequestId = 1;
    private int nextDealId = 1;
    private int nextCounterId = 1;
    private Listener listener = listing -> { };

    /**
     * Told after every successful transition, so a store can save it. A transition only ever
     * changes one listing, its requests and its deals, so the listing is all a listener needs.
     */
    @FunctionalInterface
    public interface Listener {
        void changed(Listing listing);
    }

    public DealLifecycle() {
        this(Clock.systemDefaultZone());
    }

    /** Uses {@code clock} for "now", so tests can fix the time. */
    public DealLifecycle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Sets the one listener told about every transition (replaces any earlier one). */
    public synchronized void setListener(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    // ---------- Submit ----------

    /** Records a pending price offer on a live Sale listing. */
    public synchronized PriceOffer submitOffer(Buyer buyer, Listing listing, BigDecimal price) {
        Objects.requireNonNull(listing, "listing");
        BigDecimal money = requirePositiveMoney(price, "Offer");
        PriceOffer offer = new PriceOffer(nextRequestId(), buyer, listing, money, now());
        requireLiveAndNotDealt(listing);
        return changed(register(offer));
    }

    /** Records a pending borrow request on a live Lend listing. */
    public synchronized BorrowRequest submitBorrow(Buyer buyer, Listing listing,
                                                   LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(listing, "listing");
        BorrowRequest request = new BorrowRequest(nextRequestId(), buyer, listing, start, end, now());
        validateBorrowPeriod(listing, start, end);
        requireLiveAndNotDealt(listing);
        return changed(register(request));
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
        return changed(claim);
    }

    // ---------- Counter / accept / decline ----------

    /**
     * Seller counters a pending price offer with a new price. The counter is a new record; the
     * buyer's request is only marked {@link RequestStatus#COUNTERED}. A second counter is refused
     * until this one is answered or cancelled, so a card that was sent never changes.
     */
    public synchronized CounterOffer counterOffer(PriceOffer offer, BigDecimal counterPrice) {
        requireStatus(offer, RequestStatus.PENDING);
        requireNoActiveCounter(offer);
        BigDecimal money = requirePositiveMoney(counterPrice, "Counter price");
        CounterOffer counter = new CounterOffer("C" + nextCounterId, offer, money,
                null, null, now());
        offer.setStatus(RequestStatus.COUNTERED);
        register(counter);
        changed(offer);
        return counter;
    }

    /**
     * Seller counters a pending borrow request with another period. Like a price counter this is
     * a new record, so the requested period on the buyer's card is never overwritten.
     */
    public synchronized CounterOffer counterBorrow(BorrowRequest request,
                                                   LocalDateTime start, LocalDateTime end) {
        requireStatus(request, RequestStatus.PENDING);
        requireNoActiveCounter(request);
        validateBorrowPeriod(request.getListing(), start, end);
        CounterOffer counter = new CounterOffer("C" + nextCounterId, request, null,
                start, end, now());
        request.setStatus(RequestStatus.COUNTERED);
        register(counter);
        changed(request);
        return counter;
    }

    /** Withdraws an unanswered counter; the buyer's request becomes pending again. */
    public synchronized CounterOffer cancelCounter(CounterOffer counter) {
        requireCounterStatus(counter, CounterStatus.OFFERED);
        counter.setStatus(CounterStatus.CANCELLED);
        closeCounter(counter);
        counter.getRequest().setStatus(RequestStatus.PENDING);
        changed(counter.getRequest());
        return counter;
    }

    /** Buyer accepts the seller's counter; the deal opens on the countered terms. */
    public synchronized Deal acceptCounter(CounterOffer counter) {
        requireCounterStatus(counter, CounterStatus.OFFERED);
        DealRequest request = counter.getRequest();
        requireStatus(request, RequestStatus.COUNTERED);
        // Validate the Listing before changing anything, so a refusal leaves both cards intact.
        requireLiveAndNotDealt(request.getListing());
        if (request instanceof PriceOffer offer) {
            offer.agreeCounterPrice(counter.getPrice());
        } else if (request instanceof BorrowRequest borrow) {
            borrow.agreeCounterPeriod(counter.getStartDate(), counter.getEndDate());
        }
        counter.setStatus(CounterStatus.ACCEPTED);
        closeCounter(counter);
        return changed(acceptAsDeal(request));
    }

    /** Buyer declines the seller's counter; the buyer's request is declined with it. */
    public synchronized CounterOffer declineCounter(CounterOffer counter) {
        requireCounterStatus(counter, CounterStatus.OFFERED);
        counter.setStatus(CounterStatus.DECLINED);
        closeCounter(counter);
        counter.getRequest().setStatus(RequestStatus.DECLINED);
        changed(counter.getRequest());
        return counter;
    }

    /** Seller accepts a pending request. A counter is answered by the buyer via {@link #acceptCounter}. */
    public synchronized Deal accept(DealRequest request) {
        requireStatus(request, RequestStatus.PENDING);
        return changed(acceptAsDeal(request));
    }

    /**
     * Declines a pending request. A request with an unanswered counter cannot be declined: the
     * seller cancels the counter first, or the buyer declines the counter.
     */
    public synchronized DealRequest decline(DealRequest request) {
        requireStatus(request, RequestStatus.PENDING);
        request.setStatus(RequestStatus.DECLINED);
        if (request instanceof Claim) {
            String listingId = request.getListing().getId();
            if (pendingClaimByListing.remove(listingId, request.getId())) {
                request.getListing().setStatus(ListingStatus.LIVE);
            }
        }
        return changed(request);
    }

    // ---------- Return / complete / cancel ----------

    /** Buyer returns a borrowed item. Only loans are returned. */
    public synchronized Deal returnItem(Deal deal) {
        requireBorrowedAndActive(deal, "return");
        close(deal, DealStatus.RETURNED);
        releaseListing(deal);
        return changed(deal);
    }

    /** Due date passed and the item was recorded as returned automatically. */
    public synchronized Deal autoReturn(Deal deal) {
        requireBorrowedAndActive(deal, "auto-return");
        close(deal, DealStatus.AUTO_RETURNED);
        releaseListing(deal);
        return changed(deal);
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
        return changed(deal);
    }

    public synchronized Deal cancel(Deal deal) {
        requireStatus(deal, DealStatus.ACTIVE);
        close(deal, DealStatus.CANCELLED);
        releaseListing(deal);
        return changed(deal);
    }

    // ---------- Restore (loading saved data only) ----------

    /**
     * Rebuilds a saved price offer exactly as stored, without validation, because its dates
     * and listing status are history. Only for loading the database; never call from a screen.
     */
    public synchronized PriceOffer restoreOffer(String id, Buyer buyer, Listing listing,
                                                BigDecimal offeredPrice, BigDecimal counterPrice,
                                                LocalDateTime submittedTime, RequestStatus status) {
        PriceOffer offer = new PriceOffer(id, buyer, listing, offeredPrice, submittedTime);
        if (counterPrice != null) {
            if (status == RequestStatus.COUNTERED) {
                restoreCounter(offer, counterPrice, null, null, submittedTime);
            } else {
                offer.agreeCounterPrice(counterPrice);
            }
        }
        return restored(offer, status);
    }

    /** Rebuilds a saved borrow request; see {@link #restoreOffer}. */
    public synchronized BorrowRequest restoreBorrow(String id, Buyer buyer, Listing listing,
                                                    LocalDateTime startDate, LocalDateTime endDate,
                                                    LocalDateTime counterStart, LocalDateTime counterEnd,
                                                    LocalDateTime submittedTime, RequestStatus status) {
        BorrowRequest request = new BorrowRequest(id, buyer, listing, startDate, endDate, submittedTime);
        if (counterStart != null || counterEnd != null) {
            if (status == RequestStatus.COUNTERED) {
                restoreCounter(request, null, counterStart, counterEnd, submittedTime);
            } else {
                request.agreeCounterPeriod(counterStart, counterEnd);
            }
        }
        return restored(request, status);
    }

    /** Rebuilds a saved claim; a pending one holds its listing's reservation again. */
    public synchronized Claim restoreClaim(String id, Buyer buyer, Listing listing,
                                           LocalDateTime submittedTime, RequestStatus status) {
        Claim claim = restored(new Claim(id, buyer, listing, submittedTime), status);
        if (status == RequestStatus.PENDING) {
            pendingClaimByListing.put(listing.getId(), id);
        }
        return claim;
    }

    /**
     * Rebuilds a saved deal for an already restored request; an active one is its listing's
     * active deal again. The due date is derived from the request, as for a new deal.
     */
    public synchronized Deal restoreDeal(String id, DealRequest request, LocalDateTime startDate,
                                         LocalDateTime completedDate, DealStatus status) {
        if (requests.get(request.getId()) != request) {
            throw new IllegalArgumentException("Restore request " + request.getId() + " first");
        }
        Deal deal = new Deal(id, request, startDate);
        deal.setCompletedDate(completedDate);
        deal.setStatus(Objects.requireNonNull(status, "status"));
        deals.put(id, deal);
        if (status == DealStatus.ACTIVE) {
            activeDealByListing.put(request.getListing().getId(), id);
        }
        nextDealId = Math.max(nextDealId, numericId(id, "D") + 1);
        return deal;
    }

    /** A request by id, or {@code null}. */
    public synchronized DealRequest findRequest(String id) {
        return requests.get(id);
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

    /** Every counter the sellers have sent, in send order. */
    public synchronized List<CounterOffer> counterOffers() {
        return Collections.unmodifiableList(new ArrayList<>(counters.values()));
    }

    /** The one unanswered counter on {@code request}, if any. */
    public synchronized Optional<CounterOffer> activeCounter(DealRequest request) {
        Objects.requireNonNull(request, "request");
        String counterId = activeCounterByRequest.get(request.getId());
        return Optional.ofNullable(counters.get(counterId))
                .filter(counter -> counter.getStatus() == CounterStatus.OFFERED);
    }

    /** Every deal, in the order the requests were accepted. */
    public synchronized List<Deal> deals() {
        return Collections.unmodifiableList(new ArrayList<>(deals.values()));
    }

    // ---------- Internals ----------

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private <T> T changed(T result) {
        Listing listing = result instanceof Deal deal ? deal.getListing() : ((DealRequest) result).getListing();
        listener.changed(listing);
        return result;
    }

    private void restoreCounter(DealRequest request, BigDecimal price,
                                LocalDateTime start, LocalDateTime end, LocalDateTime sentTime) {
        CounterOffer counter = new CounterOffer(
                "C" + nextCounterId++, request, price, start, end, sentTime);
        counters.put(counter.getId(), counter);
        activeCounterByRequest.put(request.getId(), counter.getId());
    }

    private <R extends DealRequest> R restored(R request, RequestStatus status) {
        if (requests.containsKey(request.getId())) {
            throw new IllegalArgumentException("Duplicate request id " + request.getId());
        }
        request.setStatus(Objects.requireNonNull(status, "status"));
        requests.put(request.getId(), request);
        nextRequestId = Math.max(nextRequestId, numericId(request.getId(), "Q") + 1);
        return request;
    }

    private static int numericId(String id, String prefix) {
        if (id == null || !id.startsWith(prefix)) {
            throw new IllegalArgumentException("Expected a " + prefix + "-id, got " + id);
        }
        return Integer.parseInt(id.substring(prefix.length()));
    }

    private String nextRequestId() {
        return "Q" + nextRequestId;
    }

    private <R extends DealRequest> R register(R request) {
        requests.put(request.getId(), request);
        nextRequestId++;
        return request;
    }

    private CounterOffer register(CounterOffer counter) {
        counters.put(counter.getId(), counter);
        activeCounterByRequest.put(counter.getRequest().getId(), counter.getId());
        nextCounterId++;
        return counter;
    }

    private void closeCounter(CounterOffer counter) {
        activeCounterByRequest.remove(counter.getRequest().getId(), counter.getId());
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

    private void requireNoActiveCounter(DealRequest request) {
        if (activeCounter(request).isPresent()) {
            throw new IllegalStateException(
                    "Cancel the current counter before sending a new one");
        }
    }

    private static void requireCounterStatus(CounterOffer counter, CounterStatus expected) {
        Objects.requireNonNull(counter, "counter");
        if (counter.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected an " + expected + " counter but was " + counter.getStatus());
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
                if (other.getStatus() == RequestStatus.COUNTERED) {
                    // The other negotiation is over too, so withdraw its outstanding counter.
                    activeCounter(other).ifPresent(counter -> {
                        counter.setStatus(CounterStatus.DECLINED);
                        closeCounter(counter);
                    });
                }
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
