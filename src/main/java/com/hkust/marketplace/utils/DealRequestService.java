package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Make Deal Request (SRS UC-1.4): a buyer's price offer, borrow request or claim on a live
 * listing. Each method checks the buyer and the listing, records the request through
 * {@link com.hkust.marketplace.models.DealLifecycle} (which validates the terms and saves it)
 * and notifies the seller.
 *
 * <p>Refusals throw with a message for the buyer: {@link SecurityException} if the actor is not
 * an active buyer, {@link IllegalStateException} if the listing is no longer available (A1) or
 * the giveaway was already claimed (A4), and {@link IllegalArgumentException} for an invalid
 * offer or borrow period (A2, A3).
 */
public final class DealRequestService {

    /** SRS UC-1.4 A1. */
    public static final String NOT_AVAILABLE = "Sorry, this item is no longer available.";
    /** SRS UC-1.4 A4. */
    public static final String ALREADY_CLAIMED = "Sorry, someone else has already claimed this item.";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final DummyDataService data;

    public DealRequestService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** S1: a pending price offer on a live Sale listing. */
    public PriceOffer submitOffer(User actor, Listing listing, BigDecimal price) {
        Buyer buyer = requireBuyer(actor);
        requireAvailable(listing, ListingType.SELL);
        PriceOffer offer = record(() -> data.deals().lifecycle().submitOffer(buyer, listing, price), null);
        notifySeller(offer, buyer.getDisplayName() + " offered " + new Money(offer.getOfferedPrice()).getDisplay()
                + " for your listing '" + listing.getTitle() + "'.");
        return offer;
    }

    /** S2: a pending borrow request on a live Lend listing. */
    public BorrowRequest submitBorrow(User actor, Listing listing, LocalDateTime start, LocalDateTime end) {
        Buyer buyer = requireBuyer(actor);
        requireAvailable(listing, ListingType.LEND);
        BorrowRequest request = record(() -> data.deals().lifecycle().submitBorrow(buyer, listing, start, end), null);
        notifySeller(request, buyer.getDisplayName() + " asked to borrow '" + listing.getTitle() + "' from "
                + start.format(WHEN) + " to " + end.format(WHEN) + ".");
        return request;
    }

    /**
     * S3: a pending claim on a live giveaway, which reserves it at once. Only the first claim
     * succeeds; a later one is refused with {@link #ALREADY_CLAIMED}.
     */
    public Claim claim(User actor, Listing listing) {
        Buyer buyer = requireBuyer(actor);
        requireAvailable(listing, ListingType.GIVEAWAY);
        Claim claim = record(() -> data.deals().lifecycle().submitClaim(buyer, listing), listing);
        notifySeller(claim, buyer.getDisplayName() + " claimed your giveaway '" + listing.getTitle() + "'.");
        return claim;
    }

    private static Buyer requireBuyer(User actor) {
        return (Buyer) AccessControl.require(actor, UserRole.BUYER);
    }

    /** A1: the listing must still be live and its seller's account active. */
    private static void requireAvailable(Listing listing, ListingType type) {
        Objects.requireNonNull(listing, "listing");
        if (listing.getType() != type) {
            throw new IllegalArgumentException("This action is only for " + type + " listings.");
        }
        if (!ListingSearch.isBrowsable(listing)) {
            throw new IllegalStateException(NOT_AVAILABLE);
        }
    }

    /**
     * Records the request. The lifecycle re-checks availability under its own lock, so if the
     * listing changed since our check its refusal is reported in the buyer's words: a giveaway
     * that is now Reserved was claimed by someone else (A4); anything else is no longer
     * available (A1).
     */
    static <R extends DealRequest> R record(Supplier<R> submit, Listing claimedGiveaway) {
        try {
            return submit.get();
        } catch (IllegalStateException e) {
            boolean claimedFirst = claimedGiveaway != null
                    && claimedGiveaway.getStatus() == ListingStatus.RESERVED;
            throw new IllegalStateException(claimedFirst ? ALREADY_CLAIMED : NOT_AVAILABLE, e);
        }
    }

    /** Step 6: the seller hears about the new request (New Request, links to the request). */
    private void notifySeller(DealRequest request, String message) {
        data.notifications().notify(request.getSellerUsername(), NotificationCategory.NEW_REQUEST, message,
                new NotificationRef(NotificationRef.Target.REQUEST, request.getId()));
    }
}
