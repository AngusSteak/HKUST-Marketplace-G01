package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Manage My Deals (SRS UC-1.6): the buyer answers a seller's counter-offer, returns a borrowed
 * item or marks a purchase or claim complete. Every change goes through {@link DealLifecycle}
 * (which saves it) and the other party is notified.
 *
 * <p>Refusals throw with a message for the buyer: {@link SecurityException} if the actor is not
 * the active buyer of that request or deal, {@link IllegalStateException} if the action no
 * longer applies (including A2 and A3, which change state before refusing).
 */
public final class MyDealsService {

    /** SRS UC-1.6 A2; the request has been declined when this is thrown. */
    public static final String COUNTER_NO_LONGER_VALID =
            "Sorry, this counter-offer can no longer be accepted because the item is no longer available."
                    + " Your request has been declined.";
    /** SRS UC-1.6 A3. */
    public static final String ALREADY_AUTO_RETURNED =
            "This item was already returned automatically because its due date has passed.";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final DummyDataService data;
    private final Clock clock;

    public MyDealsService(DummyDataService data) {
        this(data, Clock.systemDefaultZone());
    }

    /** Uses {@code clock} for "now" when deciding whether a loan is overdue, so tests can fix it. */
    MyDealsService(DummyDataService data, Clock clock) {
        this.data = Objects.requireNonNull(data, "data");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---------- Respond to counter (step 3.2) ----------

    /**
     * Accepts the seller's counter: the request becomes Accepted with the countered terms, a deal
     * opens, the listing is Reserved or Lent, and the other open requests on it are declined.
     * If the listing is no longer available the request is declined instead (A2).
     */
    public Deal acceptCounter(User actor, DealRequest request) {
        Buyer buyer = requireOwnCounter(actor, request);
        Listing listing = request.getListing();
        if (!ListingSearch.isBrowsable(listing)) {
            throw declineBecauseUnavailable(request);
        }
        List<DealRequest> othersOpen = lifecycle().requestsFor(listing).stream()
                .filter(r -> r != request && isOpen(r))
                .toList();
        Deal deal;
        try {
            deal = lifecycle().acceptCounter(request);
        } catch (IllegalStateException e) {
            // Another request on the listing was accepted first.
            throw declineBecauseUnavailable(request);
        }
        notify(request.getSellerUsername(), NotificationCategory.NEW_REQUEST,
                buyer.getDisplayName() + " accepted your counter-offer (" + terms(request) + ") for '"
                        + listing.getTitle() + "'.", NotificationRef.Target.DEAL, deal.getId());
        for (DealRequest other : othersOpen) {
            if (other.getStatus() == RequestStatus.DECLINED) {
                notify(other.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                        "Your request for '" + listing.getTitle() + "' was declined because the item is no longer"
                                + " available.", NotificationRef.Target.REQUEST, other.getId());
            }
        }
        return deal;
    }

    /** Declines the seller's counter; the request is Declined and the seller is told. */
    public DealRequest declineCounter(User actor, DealRequest request) {
        Buyer buyer = requireOwnCounter(actor, request);
        lifecycle().decline(request);
        notify(request.getSellerUsername(), NotificationCategory.NEW_REQUEST,
                buyer.getDisplayName() + " declined your counter-offer for '" + request.getListing().getTitle() + "'.",
                NotificationRef.Target.REQUEST, request.getId());
        return request;
    }

    // ---------- Return item (step 3.3) ----------

    /**
     * Returns a borrowed item: the deal is Returned with today's date, the listing is Live again
     * and the seller is told. If the due date has passed the loan is auto-returned instead (or
     * already was) and the buyer is told so (A3).
     */
    public Deal returnItem(User actor, Deal deal) {
        Buyer buyer = requireOwnDeal(actor, deal);
        if (deal.getDealType() != DealType.LENT) {
            throw new IllegalStateException("Only a borrowed item can be returned.");
        }
        if (deal.getStatus() == DealStatus.AUTO_RETURNED) {
            throw new IllegalStateException(ALREADY_AUTO_RETURNED);
        }
        if (deal.getStatus() != DealStatus.ACTIVE) {
            throw new IllegalStateException("This item has already been returned.");
        }
        if (!LocalDateTime.now(clock).isBefore(deal.getDueDate())) {
            autoReturn(deal);
            throw new IllegalStateException(ALREADY_AUTO_RETURNED);
        }
        lifecycle().returnItem(deal);
        notify(deal.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                buyer.getDisplayName() + " returned '" + deal.getListing().getTitle() + "'. It is live again.",
                NotificationRef.Target.DEAL, deal.getId());
        return deal;
    }

    /**
     * Auto-returns an overdue loan (SRS UC-1.6 special requirements): the deal is Auto-Returned,
     * the listing Live again, and both parties are told. A system action with no actor, also
     * used by the due-date timer (#22). Does nothing if the loan is no longer active.
     *
     * @return whether the loan was auto-returned now
     */
    public boolean autoReturn(Deal deal) {
        Objects.requireNonNull(deal, "deal");
        if (deal.getDealType() != DealType.LENT || deal.getStatus() != DealStatus.ACTIVE) {
            return false;
        }
        lifecycle().autoReturn(deal);
        String title = deal.getListing().getTitle();
        notify(deal.getBuyerUsername(), NotificationCategory.BORROW_REMINDER,
                "'" + title + "' was due " + deal.getDueDate().format(WHEN) + " and has been marked as returned.",
                NotificationRef.Target.DEAL, deal.getId());
        notify(deal.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                "The loan of '" + title + "' to " + deal.getBuyerDisplayName()
                        + " passed its due date and was marked as returned. It is live again.",
                NotificationRef.Target.DEAL, deal.getId());
        return true;
    }

    // ---------- Mark complete (step 3.4) ----------

    /**
     * The buyer confirms receiving a purchased or claimed item: the deal is Completed and the
     * listing Sold or Given, and the seller is told. The screen then prompts for a rating.
     */
    public Deal markComplete(User actor, Deal deal) {
        Buyer buyer = requireOwnDeal(actor, deal);
        if (deal.getDealType() == DealType.LENT) {
            throw new IllegalStateException("A borrowed item is returned, not marked complete.");
        }
        if (deal.getStatus() != DealStatus.ACTIVE) {
            throw new IllegalStateException("This deal is already " + deal.getStatus().name().toLowerCase() + ".");
        }
        lifecycle().complete(deal);
        notify(deal.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                buyer.getDisplayName() + " received '" + deal.getListing().getTitle() + "'. The deal is complete.",
                NotificationRef.Target.DEAL, deal.getId());
        return deal;
    }

    // ---------- Internals ----------

    private DealLifecycle lifecycle() {
        return data.deals().lifecycle();
    }

    /** A2: declines the request and returns the exception that tells the buyer why. */
    private IllegalStateException declineBecauseUnavailable(DealRequest request) {
        lifecycle().decline(request);
        notify(request.getSellerUsername(), NotificationCategory.NEW_REQUEST,
                request.getBuyerDisplayName() + "'s request for '" + request.getListing().getTitle()
                        + "' was declined: the counter-offer could not be accepted because the item is no longer"
                        + " available.", NotificationRef.Target.REQUEST, request.getId());
        return new IllegalStateException(COUNTER_NO_LONGER_VALID);
    }

    private Buyer requireOwnCounter(User actor, DealRequest request) {
        Objects.requireNonNull(request, "request");
        Buyer buyer = requireBuyer(actor, request.getBuyerUsername());
        if (request.getStatus() != RequestStatus.COUNTERED) {
            throw new IllegalStateException("There is no counter-offer to respond to on this request.");
        }
        return buyer;
    }

    private Buyer requireOwnDeal(User actor, Deal deal) {
        Objects.requireNonNull(deal, "deal");
        return requireBuyer(actor, deal.getBuyerUsername());
    }

    private static Buyer requireBuyer(User actor, String owner) {
        Buyer buyer = (Buyer) AccessControl.require(actor, UserRole.BUYER);
        if (!buyer.getUsername().equalsIgnoreCase(owner)) {
            throw new SecurityException("This is not your deal.");
        }
        return buyer;
    }

    private static boolean isOpen(DealRequest request) {
        return request.getStatus() == RequestStatus.PENDING || request.getStatus() == RequestStatus.COUNTERED;
    }

    /** The agreed terms in words: a price, or a borrow period. */
    static String terms(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return new Money(offer.agreedPrice()).getDisplay();
        }
        if (request instanceof BorrowRequest borrow) {
            return borrow.agreedStart().format(WHEN) + " to " + borrow.agreedEnd().format(WHEN);
        }
        return "claim";
    }

    private void notify(String recipient, NotificationCategory category, String message,
                        NotificationRef.Target target, String id) {
        data.notifications().notify(recipient, category, message, new NotificationRef(target, id));
    }
}
