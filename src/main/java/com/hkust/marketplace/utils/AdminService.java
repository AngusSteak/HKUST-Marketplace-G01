package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.time.LocalDateTime;
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

    /** Approves a pending listing: it goes live and the seller is notified (SRS UC-3.1). */
    public void approveListing(User admin, Listing listing) {
        AccessControl.require(admin, UserRole.ADMIN);
        requirePending(listing);
        listing.setStatus(ListingStatus.LIVE);
        listing.setReviewedAt(LocalDateTime.now());
        data.listings().update(listing);
        data.notifications().notify(listing.getSellerUsername(), NotificationCategory.LISTING_APPROVED,
                "Your listing '" + listing.getTitle() + "' is now live!",
                new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
    }

    /** Rejects a pending listing with a reason the seller sees (SRS UC-3.1). */
    public void rejectListing(User admin, Listing listing, String reason) {
        AccessControl.require(admin, UserRole.ADMIN);
        requirePending(listing);
        String why = requireText(reason, "Rejection reason");
        listing.setStatus(ListingStatus.REJECTED);
        listing.setRejectionReason(why);
        listing.setReviewedAt(LocalDateTime.now());
        data.listings().update(listing);
        data.notifications().notify(listing.getSellerUsername(), NotificationCategory.LISTING_REJECTED,
                "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + why,
                new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
    }

    /**
     * Takes a listing off the marketplace. It keeps its deals, chats and reports, which is why
     * listings are taken down by status rather than deleted (#34 adds the full flow).
     */
    public void takeDownListing(User admin, Listing listing, String reason) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        listing.setStatus(ListingStatus.TAKEN_DOWN);
        listing.setTakedownReason(requireText(reason, "Take-down reason"));
        data.listings().update(listing);
        data.audit().recordTakeDown(admin.getUsername(), listing.getId());
    }

    /** Activates or deactivates an account (SRS UC-3.4). */
    public void setActive(User admin, User account, boolean active) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
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
