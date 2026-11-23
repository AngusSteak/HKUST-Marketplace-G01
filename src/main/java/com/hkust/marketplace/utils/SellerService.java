package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
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

    private final DummyDataService data;

    public SellerService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Deletes one of the seller's own listings that nobody has acted on yet.
     *
     * @throws SecurityException if {@code seller} is not an active Seller who owns the listing
     * @throws IllegalStateException with {@link #CANNOT_DELETE} if the listing has requests,
     *         deals, chats or reports (the database keeps them pointing at it)
     */
    public void deleteListing(User seller, Listing listing) {
        AccessControl.require(seller, UserRole.SELLER);
        Objects.requireNonNull(listing, "listing");
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own listing " + listing.getId());
        }
        try {
            data.listings().remove(listing.getId());
        } catch (DatabaseException e) {
            throw new IllegalStateException(CANNOT_DELETE, e);
        }
    }
}
