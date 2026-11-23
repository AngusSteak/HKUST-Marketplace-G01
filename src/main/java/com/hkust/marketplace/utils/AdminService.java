package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Admin-only actions. Every method first checks that {@code admin} is an active Admin account
 * ({@link AccessControl}) and throws {@link SecurityException} otherwise, before changing
 * anything. Each action saves through the stores and sends the notification or audit entry
 * that goes with it, so the admin screens only collect input and call these.
 */
public final class AdminService {

    private final DummyDataService data;

    public AdminService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** The take-down note on a listing's open reports (SRS UC-3.7 S1 step 3). */
    public static final String TAKEN_DOWN_RESOLUTION = "Listing taken down";

    /**
     * Approves a pending listing: it goes live and the seller is notified (SRS UC-3.2). The status,
     * review date and notification are saved together.
     */
    public void approveListing(User admin, Listing listing) {
        AccessControl.require(admin, UserRole.ADMIN);
        requirePending(listing);
        data.database().inTransaction(() -> {
            listing.setStatus(ListingStatus.LIVE);
            listing.setReviewedAt(LocalDateTime.now());
            data.listings().update(listing);
            data.notifications().notify(listing.getSellerUsername(), NotificationCategory.LISTING_APPROVED,
                    "Your listing '" + listing.getTitle() + "' is now live!",
                    new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
        });
    }

    /** Rejects a pending listing with a reason the seller sees, saved together (SRS UC-3.2). */
    public void rejectListing(User admin, Listing listing, String reason) {
        AccessControl.require(admin, UserRole.ADMIN);
        requirePending(listing);
        String why = requireText(reason, "Rejection reason");
        data.database().inTransaction(() -> {
            listing.setStatus(ListingStatus.REJECTED);
            listing.setRejectionReason(why);
            listing.setReviewedAt(LocalDateTime.now());
            data.listings().update(listing);
            data.notifications().notify(listing.getSellerUsername(), NotificationCategory.LISTING_REJECTED,
                    "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + why,
                    new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
        });
    }

    /**
     * Takes a live listing off the marketplace (SRS UC-3.7 S1): stores the reason and the
     * Taken Down status, records it in the audit log, resolves the open reports with
     * {@value #TAKEN_DOWN_RESOLUTION}, declines the pending and countered requests, and notifies
     * those buyers and the seller. The listing keeps its deals, chats and reports.
     *
     * <p>Only Live listings for now: a Reserved or Lent listing first needs its deal cancelled
     * (A2), which #34 adds; other statuses cannot be taken down (A3).
     */
    public void takeDownListing(User admin, Listing listing, String reason) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        String why = requireText(reason, "Take-down reason");
        switch (listing.getStatus()) {
            case LIVE -> { }
            case RESERVED, LENT -> throw new IllegalStateException(
                    "A deal is in progress on this listing; cancel the deal before taking it down.");
            default -> throw new IllegalStateException(
                    "Only a Live, Reserved or Lent listing can be taken down.");
        }
        List<DealRequest> open = data.deals().lifecycle().requestsFor(listing).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED)
                .toList();
        NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, listing.getId());
        data.database().inTransaction(() -> {
            open.forEach(r -> data.deals().lifecycle().decline(r));
            listing.setStatus(ListingStatus.TAKEN_DOWN);
            listing.setTakedownReason(why);
            data.listings().update(listing);
            data.audit().recordTakeDown(admin.getUsername(), listing.getId());
            data.reports().resolveAll(listing, TAKEN_DOWN_RESOLUTION);
            for (DealRequest r : open) {
                data.notifications().notify(r.getBuyerUsername(), NotificationCategory.LISTING_REMOVED,
                        "'" + listing.getTitle() + "' was removed from the marketplace, so your request was declined.",
                        ref);
            }
            data.notifications().notify(listing.getSellerUsername(), NotificationCategory.LISTING_REMOVED,
                    "Your listing '" + listing.getTitle() + "' was taken down. Reason: " + why, ref);
        });
    }

    /**
     * Activates or deactivates a buyer or seller account (SRS UC-3.4). The admin account
     * cannot be disabled.
     */
    public void setActive(User admin, User account, boolean active) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
        if (account.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException("The admin account cannot be disabled or deleted.");
        }
        account.setActive(active);
        data.users().save(account);
    }

    /** The admin "Delete" button: deactivates the account for now (#33 adds soft delete). */
    public void deactivateAccount(User admin, User account) {
        setActive(admin, account, false);
        data.audit().recordAccountDeletion(admin.getUsername(), "@" + account.getUsername());
    }

    /** Hides or unhides a review; hidden reviews leave the seller's rating (SRS UC-3.6). */
    public void setReviewHidden(User admin, Review review, boolean hidden) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(review, "review");
        review.setHidden(hidden);
        data.reviews().save(review);
    }

    private static void requirePending(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Listing " + listing.getId() + " is not pending review");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
        return value.trim();
    }
}
