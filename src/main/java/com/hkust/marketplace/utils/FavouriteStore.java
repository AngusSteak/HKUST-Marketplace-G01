package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Seller;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory Favourites (Buyer to Listing) and Follows (Buyer to Seller) associations, both
 * many-to-many with no duplicates (SRS class diagram, UC-1.3 S1). Lists keep the order in
 * which items were added. Keys are lower-cased usernames, matching {@link UserStore}.
 */
public class FavouriteStore {

    /** buyer username -> favourite listing ids */
    private final Map<String, Set<String>> favourites = new HashMap<>();
    /** buyer username -> followed seller usernames */
    private final Map<String, Set<String>> follows = new HashMap<>();

    /** Adds the listing to the buyer's favourites, or removes it if already there. Returns the new state. */
    public boolean toggleFavourite(Buyer buyer, Listing listing) {
        Objects.requireNonNull(listing, "listing");
        return toggle(favourites, key(buyer), Objects.requireNonNull(listing.getId(), "listing id"));
    }

    /** Follows the seller, or unfollows if already following. Returns the new state. */
    public boolean toggleFollow(Buyer buyer, Seller seller) {
        Objects.requireNonNull(seller, "seller");
        return toggle(follows, key(buyer), key(seller.getUsername()));
    }

    public boolean isFavourite(Buyer buyer, Listing listing) {
        return favourites.getOrDefault(key(buyer), Set.of()).contains(listing.getId());
    }

    public boolean isFollowing(Buyer buyer, Seller seller) {
        return follows.getOrDefault(key(buyer), Set.of()).contains(key(seller.getUsername()));
    }

    /**
     * The buyer's favourite listing ids, oldest first. Ids are kept even if a listing is later
     * removed, so callers resolve them through {@link ListingStore#find} and skip missing ones.
     */
    public List<String> favouriteListingIds(Buyer buyer) {
        return List.copyOf(favourites.getOrDefault(key(buyer), Set.of()));
    }

    /** Usernames of the sellers the buyer follows, oldest first. */
    public List<String> followedSellers(Buyer buyer) {
        return List.copyOf(follows.getOrDefault(key(buyer), Set.of()));
    }

    /** Usernames of the buyers following the seller, e.g. to notify them of a new listing. */
    public List<String> followersOf(Seller seller) {
        String sellerKey = key(seller.getUsername());
        return follows.entrySet().stream()
                .filter(e -> e.getValue().contains(sellerKey))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
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
