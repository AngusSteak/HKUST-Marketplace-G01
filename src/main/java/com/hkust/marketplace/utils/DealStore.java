package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.persistence.Database;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Deal requests and deals. All state changes go through {@link #lifecycle()}; after every
 * transition this store saves the listing's requests, deals and status to the
 * {@code deal_requests}, {@code deals} and {@code listings} tables, and {@link #load} rebuilds
 * them at startup. Otherwise it only answers queries for the portals.
 */
public class DealStore {

    private final DealLifecycle lifecycle = new DealLifecycle();
    private final Database db;
    private final ListingStore listings;

    DealStore(Database db, ListingStore listings) {
        this.db = Objects.requireNonNull(db, "db");
        this.listings = Objects.requireNonNull(listings, "listings");
        lifecycle.setListener(this::save);
    }

    /** The transition engine; use it to submit, counter, accept, return or complete. */
    public DealLifecycle lifecycle() {
        return lifecycle;
    }

    /** Every request, in submit order. */
    public List<DealRequest> allRequests() {
        return lifecycle.requests();
    }

    /** Every deal, in the order the requests were accepted. */
    public List<Deal> allDeals() {
        return lifecycle.deals();
    }

    /** A request by id (e.g. the one behind a Formal Offer message), or {@code null}. */
    public DealRequest findRequest(String id) {
        return lifecycle.findRequest(id);
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

    /** Every deal the buyer is part of, in the order the deals were accepted. */
    public List<Deal> dealsForBuyer(String username) {
        return lifecycle.deals().stream()
                .filter(d -> username.equalsIgnoreCase(d.getBuyerUsername()))
                .toList();
    }

    /** Every deal the seller is part of, in the order the deals were accepted. */
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

    // ---------- Persistence ----------

    /** Saves the listing's requests and deals, then the listing itself (its status changed). */
    private void save(Listing listing) {
        db.inTransaction(() -> {
            for (DealRequest request : lifecycle.requestsFor(listing)) {
                saveRequest(request);
            }
            for (Deal deal : lifecycle.deals()) {
                if (deal.getListing().getId().equals(listing.getId())) {
                    saveDeal(deal);
                }
            }
            listings.update(listing);
        });
    }

    private void saveRequest(DealRequest request) {
        BigDecimal offered = null, counter = null;
        LocalDateTime start = null, end = null, counterStart = null, counterEnd = null;
        String type;
        if (request instanceof PriceOffer offer) {
            type = "OFFER";
            offered = offer.getOfferedPrice();
            counter = offer.getCounterPrice();
        } else if (request instanceof BorrowRequest borrow) {
            type = "BORROW";
            start = borrow.getStartDate();
            end = borrow.getEndDate();
            counterStart = borrow.getCounterStart();
            counterEnd = borrow.getCounterEnd();
        } else {
            type = "CLAIM";
        }
        db.update("INSERT INTO deal_requests (id, type, buyer_username, listing_id, submitted_time, status,"
                        + " offered_price, counter_price, start_date, end_date, counter_start, counter_end)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (id) DO UPDATE SET status = excluded.status,"
                        + " counter_price = excluded.counter_price, counter_start = excluded.counter_start,"
                        + " counter_end = excluded.counter_end",
                request.getId(), type, request.getBuyerUsername().toLowerCase(), request.getListing().getId(),
                request.getSubmittedTime(), request.getStatus(), offered, counter, start, end,
                counterStart, counterEnd);
    }

    private void saveDeal(Deal deal) {
        db.update("INSERT INTO deals (id, request_id, start_date, due_date, completed_date, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (id) DO UPDATE SET completed_date = excluded.completed_date,"
                        + " status = excluded.status",
                deal.getId(), deal.getRequest().getId(), deal.getStartDate(), deal.getDueDate(),
                deal.getCompletedDate(), deal.getStatus());
    }

    /**
     * Rebuilds the saved requests and deals, in the order they were made, on the already loaded
     * users and listings. Pending claims and active deals hold their listings again, so the
     * "first claim wins" and "one active deal per listing" rules still hold after a restart.
     */
    void load(UserStore users, ListingStore listingStore) {
        db.query("SELECT * FROM deal_requests ORDER BY CAST(substr(id, 2) AS INTEGER)",
                row -> restoreRequest(row, users, listingStore));
        db.query("SELECT * FROM deals ORDER BY CAST(substr(id, 2) AS INTEGER)", row -> lifecycle.restoreDeal(
                row.getString("id"),
                lifecycle.findRequest(row.getString("request_id")),
                Database.dateTime(row, "start_date"),
                Database.dateTime(row, "completed_date"),
                DealStatus.valueOf(row.getString("status"))));
    }

    private DealRequest restoreRequest(ResultSet row, UserStore users, ListingStore listingStore)
            throws SQLException {
        String id = row.getString("id");
        if (!(users.find(row.getString("buyer_username")) instanceof Buyer buyer)) {
            throw new IllegalStateException("Request " + id + " has unknown buyer "
                    + row.getString("buyer_username"));
        }
        Listing listing = listingStore.find(row.getString("listing_id"));
        if (listing == null) {
            throw new IllegalStateException("Request " + id + " is on unknown listing "
                    + row.getString("listing_id"));
        }
        LocalDateTime submitted = Database.dateTime(row, "submitted_time");
        RequestStatus status = RequestStatus.valueOf(row.getString("status"));
        return switch (row.getString("type")) {
            case "OFFER" -> lifecycle.restoreOffer(id, buyer, listing, Database.decimal(row, "offered_price"),
                    Database.decimal(row, "counter_price"), submitted, status);
            case "BORROW" -> lifecycle.restoreBorrow(id, buyer, listing,
                    Database.dateTime(row, "start_date"), Database.dateTime(row, "end_date"),
                    Database.dateTime(row, "counter_start"), Database.dateTime(row, "counter_end"),
                    submitted, status);
            case "CLAIM" -> lifecycle.restoreClaim(id, buyer, listing, submitted, status);
            default -> throw new IllegalStateException("Unknown request type " + row.getString("type"));
        };
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
