package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.persistence.Database;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Favourites (Buyer to Listing) and Follows (Buyer to Seller) associations, both
 * many-to-many with no duplicates (SRS class diagram, UC-1.3 S1). Lists keep the order in
 * which items were added. Keys are lower-cased usernames, matching {@link UserStore}.
 * Every toggle is saved to the {@code favourites} / {@code follows} tables; {@link #load()}
 * rebuilds them at startup.
 */
public class FavouriteStore {

    private final Database db;

    /** buyer username -> favourite listing ids */
    private final Map<String, Set<String>> favourites = new HashMap<>();
    /** buyer username -> followed seller usernames */
    private final Map<String, Set<String>> follows = new HashMap<>();

    FavouriteStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    /** Adds the listing to the buyer's favourites, or removes it if already there. Returns the new state. */
    public synchronized boolean toggleFavourite(Buyer buyer, Listing listing) {
        Objects.requireNonNull(listing, "listing");
        String owner = key(buyer);
        String listingId = Objects.requireNonNull(listing.getId(), "listing id");
        boolean added = !favourites.getOrDefault(owner, Set.of()).contains(listingId);
        if (added) {
            db.update("INSERT INTO favourites (buyer_username, listing_id, position)"
                    + " VALUES (?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM favourites))", owner, listingId);
        } else {
            db.update("DELETE FROM favourites WHERE buyer_username = ? AND listing_id = ?", owner, listingId);
        }
        return toggle(favourites, owner, listingId);
    }

    /** Follows the seller, or unfollows if already following. Returns the new state. */
    public synchronized boolean toggleFollow(Buyer buyer, Seller seller) {
        Objects.requireNonNull(seller, "seller");
        String owner = key(buyer);
        String sellerKey = key(seller.getUsername());
        boolean added = !follows.getOrDefault(owner, Set.of()).contains(sellerKey);
        if (added) {
            db.update("INSERT INTO follows (buyer_username, seller_username, position)"
                    + " VALUES (?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM follows))", owner, sellerKey);
        } else {
            db.update("DELETE FROM follows WHERE buyer_username = ? AND seller_username = ?", owner, sellerKey);
        }
        return toggle(follows, owner, sellerKey);
    }

    /** Whether the buyer has favourited the listing. */
    public synchronized boolean isFavourite(Buyer buyer, Listing listing) {
        return favourites.getOrDefault(key(buyer), Set.of()).contains(listing.getId());
    }

    /** Whether the buyer follows the seller. */
    public synchronized boolean isFollowing(Buyer buyer, Seller seller) {
        return follows.getOrDefault(key(buyer), Set.of()).contains(key(seller.getUsername()));
    }

    /**
     * The buyer's favourite listing ids, oldest first. Ids are kept even if a listing is later
     * removed, so callers resolve them through {@link ListingStore#find} and skip missing ones.
     */
    public synchronized List<String> favouriteListingIds(Buyer buyer) {
        return List.copyOf(favourites.getOrDefault(key(buyer), Set.of()));
    }

    /** Usernames of the sellers the buyer follows, oldest first. */
    public synchronized List<String> followedSellers(Buyer buyer) {
        return List.copyOf(follows.getOrDefault(key(buyer), Set.of()));
    }

    /** Usernames of the buyers following the seller, e.g. to notify them of a new listing. */
    public synchronized List<String> followersOf(Seller seller) {
        String sellerKey = key(seller.getUsername());
        return follows.entrySet().stream()
                .filter(e -> e.getValue().contains(sellerKey))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Rebuilds favourites and follows from the database, in the order they were added. */
    synchronized void load() {
        db.query("SELECT buyer_username, listing_id FROM favourites ORDER BY position", row ->
                favourites.computeIfAbsent(row.getString(1), k -> new LinkedHashSet<>()).add(row.getString(2)));
        db.query("SELECT buyer_username, seller_username FROM follows ORDER BY position", row ->
                follows.computeIfAbsent(row.getString(1), k -> new LinkedHashSet<>()).add(row.getString(2)));
    }

    void seed(Buyer alice, Listing favourite, Seller followed) {
        toggleFavourite(alice, favourite);
        toggleFollow(alice, followed);
    }

    private static boolean toggle(Map<String, Set<String>> links, String owner, String target) {
        Set<String> targets = links.computeIfAbsent(owner, k -> new LinkedHashSet<>());
        if (targets.remove(target)) {
            return false;
        }
        targets.add(target);
        return true;
    }

    private static String key(Buyer buyer) {
        Objects.requireNonNull(buyer, "buyer");
        return key(buyer.getUsername());
    }

    private static String key(String username) {
        return Objects.requireNonNull(username, "username").toLowerCase();
    }
}
