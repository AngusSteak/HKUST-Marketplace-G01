package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.DatabaseException;

import java.util.Objects;

/**
 * Seller-only actions on the seller's own listings. Every method checks that {@code seller}
 * is an active Seller account that owns the listing, and throws {@link SecurityException}
 * otherwise, before changing anything.
 */
public final class SellerService {

    /** Why a listing someone has acted on cannot be deleted. */
    public static final String CANNOT_DELETE =
            "This listing has offers, deals, chats or reports, so it can't be deleted.";

    /** SRS UC-2.4 A2: a listing that is no longer live or pending can't be changed. */
    public static final String NOT_CHANGEABLE = "This listing can no longer be changed.";

    private final DummyDataService data;

    public SellerService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Deletes one of the seller's own listings that nobody has acted on yet.
     *
     * @throws SecurityException if {@code seller} is not an active Seller who owns the listing
     * @throws IllegalStateException with {@link #NOT_CHANGEABLE} if the listing is Reserved,
     *         Sold, Lent, Given or Taken Down (SRS UC-2.4 A2), or with {@link #CANNOT_DELETE} if
     *         it has requests, deals, chats or reports (the database keeps them pointing at it;
     *         #26 adds deleting with open offers, UC-2.4 A1)
     */
    public void deleteListing(User seller, Listing listing) {
        AccessControl.require(seller, UserRole.SELLER);
        Objects.requireNonNull(listing, "listing");
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own listing " + listing.getId());
        }
        ListingStatus status = listing.getStatus();
        if (status != ListingStatus.LIVE && status != ListingStatus.PENDING_REVIEW
                && status != ListingStatus.REJECTED) {
            throw new IllegalStateException(NOT_CHANGEABLE);
        }
        try {
            data.listings().remove(listing.getId());
        } catch (DatabaseException e) {
            throw new IllegalStateException(CANNOT_DELETE, e);
        }
    }
}
