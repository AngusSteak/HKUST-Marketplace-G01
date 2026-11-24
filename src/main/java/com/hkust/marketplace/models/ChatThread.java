package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * The private conversation about one Listing between that Listing's Buyer and Seller
 * (GLOSSARY). A thread is uniquely identified by the Buyer and the Listing; the Seller is
 * always derived from the Listing and never stored separately. Threads are never deleted,
 * so a conversation stays available after its Listing stops being live.
 */
public final class ChatThread {

    private final Buyer buyer;
    private final Listing listing;

    /** Creates the one chat thread for a buyer and listing pair. */
    public ChatThread(Buyer buyer, Listing listing) {
        this.buyer = Objects.requireNonNull(buyer, "buyer");
        this.listing = Objects.requireNonNull(listing, "listing");
    }

    public Buyer getBuyer() { return buyer; }

    public Listing getListing() { return listing; }

    /** Derived from the Listing; do not store a second Seller reference on the thread. */
    public Seller getSeller() { return listing.getSeller(); }
}
