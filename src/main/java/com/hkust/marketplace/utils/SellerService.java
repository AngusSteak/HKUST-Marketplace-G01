package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.forms.PhotoDraft;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seller-only actions on the seller's own listings. Every method checks that {@code seller}
 * is an active Seller account that owns the listing, and throws {@link SecurityException}
 * otherwise, before changing anything.
 */
public final class SellerService {

    /** SRS UC-2.4 A2: a listing that is no longer live or pending can't be changed. */
    public static final String NOT_CHANGEABLE = "This listing can no longer be changed.";

    public static final String HAS_ACTIVE_OFFERS = "This listing has open offers and cannot be edited.";

    /** SRS UC-2.5 A2: only a live listing can be marked as sold. */
    public static final String NOT_LIVE = "Only a live listing can be marked as sold.";

    /** SRS UC-2.5 A3: only a Sale listing can be marked as sold. */
    public static final String NOT_SALE_LISTING = "Only a Sale listing can be marked as sold.";

    private final DummyDataService data;

    public SellerService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Creates a listing from the reusable form for an active Seller, notifying every admin.
     *
     * @throws SecurityException if {@code actor} is not a signed-in, active Seller
     * @throws IllegalArgumentException if the form state still has validation errors
     */
    public Listing createListing(User actor, ListingFormState state, PhotoStorage photos) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(photos, "photos");

        Map<String, String> errors = state.validate(seller);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid listing: " + errors);
        }

        String id = data.listings().reserveId();
        List<String> storedPaths = new ArrayList<>();
        for (PhotoDraft draft : state.getChosenPhotos()) {
            storedPaths.add(photos.store(draft.source(), seller.getUsername(), id));
        }
        state.setPhotoPaths(storedPaths);

        Listing listing = state.buildListing(seller);
        listing.setId(id);

        try {
            data.database().inTransaction(() -> {
                data.listings().add(listing);
                for (User admin : data.users().byRole(UserRole.ADMIN)) {
                    data.notifications().notify(admin.getUsername(), NotificationCategory.NEW_SUBMISSION,
                            seller.getFullName() + " submitted '" + listing.getTitle() + "' for review.",
                            new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
                }
            });
        } catch (RuntimeException e) {
            for (String path : storedPaths) {
                try {
                    Files.deleteIfExists(Path.of(path));
                } catch (IOException ignored) {
                    // best-effort cleanup of a photo whose listing was never saved
                }
            }
            throw e;
        }
        return listing;
    }

    public Listing updateListing(User actor, Listing listing, ListingFormState state, PhotoStorage photos) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(photos, "photos");
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own listing " + listing.getId());
        }
        ListingStatus status = listing.getStatus();
        if (listing.isDeleted() || (status != ListingStatus.LIVE && status != ListingStatus.PENDING_REVIEW
                && status != ListingStatus.REJECTED)) {
            throw new IllegalStateException(NOT_CHANGEABLE);
        }
        boolean hasOpenOffers = data.deals().lifecycle().requestsFor(listing).stream()
                .anyMatch(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED);
        if (hasOpenOffers) {
            throw new IllegalStateException(HAS_ACTIVE_OFFERS);
        }

        Map<String, String> errors = state.validate(seller);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid listing: " + errors);
        }
        if (state.getType() != listing.getType()) {
            throw new IllegalArgumentException("Listing type cannot be changed.");
        }

        listing.setTitle(state.getTitle());
        listing.setDescription(state.getDescription());
        listing.setCategory(state.getCategory());
        listing.setCondition(state.getCondition());
        listing.setMeetupLocation(state.getMeetupLocation());

        List<String> paths = new ArrayList<>(listing.getPhotoPaths());
        for (PhotoDraft draft : state.getChosenPhotos()) {
            if (paths.size() >= ListingFormState.MAX_PHOTOS) {
                break;
            }
            paths.add(photos.store(draft.source(), seller.getUsername(), listing.getId()));
        }
        listing.setPhotoPaths(paths);

        if (listing instanceof SaleListing sale) {
            sale.setPrice(Money.of(state.getRawPrice()));
        } else if (listing instanceof LendListing lend) {
            lend.setLendingDuration(state.getLendingDuration());
        }

        listing.setStatus(ListingStatus.PENDING_REVIEW);
        listing.setRejectionReason(null);
        listing.setTakedownReason(null);
        listing.setReviewedAt(null);

        data.database().inTransaction(() -> {
            data.listings().update(listing);
            NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, listing.getId());
            for (User admin : data.users().byRole(UserRole.ADMIN)) {
                data.notifications().notify(admin.getUsername(), NotificationCategory.NEW_SUBMISSION,
                        seller.getFullName() + " updated '" + listing.getTitle() + "' and resubmitted it for review.",
                        ref);
            }
        });
        return listing;
    }

    /**
     * Withdraws one of the seller's own listings (SRS UC-2.4 A1). Open requests are declined,
     * their buyers get a LISTING_REMOVED notice and the listing is hidden — all in one
     * transaction, so a failure part-way through changes nothing. The row is kept so chat, deal
     * and notification history can still resolve the listing by id.
     *
     * @throws SecurityException if {@code seller} is not an active Seller who owns the listing
     * @throws IllegalStateException with {@link #NOT_CHANGEABLE} if the listing is Reserved,
     *         Sold, Lent, Given or Taken Down (SRS UC-2.4 A2)
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
        List<DealRequest> open = data.deals().lifecycle().requestsFor(listing).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED)
                .toList();
        NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, listing.getId());
        data.database().inTransaction(() -> {
            open.forEach(r -> data.deals().lifecycle().decline(r));
            for (DealRequest r : open) {
                data.notifications().notify(r.getBuyerUsername(), NotificationCategory.LISTING_REMOVED,
                        "'" + listing.getTitle() + "' was withdrawn from the marketplace, so your request was declined.",
                        ref);
            }
            listing.setDeleted(true);
            data.listings().update(listing);
        });
    }

    /**
     * Marks one of the seller's own live Sale listings as sold (SRS UC-2.5). Open requests are
     * declined and their buyers get an OFFER_UPDATE notice, then the listing is set to SOLD — all
     * in one transaction, so a failure part-way through changes nothing. No deal is created:
     * completing a deal stays the shared deal lifecycle's job.
     *
     * @throws SecurityException if {@code actor} is not an active Seller who owns the listing
     * @throws IllegalStateException with {@link #NOT_LIVE}, {@link #NOT_SALE_LISTING} or
     *         {@link #NOT_CHANGEABLE} if the listing is not a visible, live Sale listing
     */
    public void markSold(User actor, Listing listing) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(listing, "listing");
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own listing " + listing.getId());
        }
        if (listing.isDeleted()) {
            throw new IllegalStateException(NOT_CHANGEABLE);
        }
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException(NOT_LIVE);
        }
        if (!(listing instanceof SaleListing)) {
            throw new IllegalStateException(NOT_SALE_LISTING);
        }
        List<DealRequest> open = data.deals().lifecycle().requestsFor(listing).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED)
                .toList();
        NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, listing.getId());
        data.database().inTransaction(() -> {
            open.forEach(r -> data.deals().lifecycle().decline(r));
            for (DealRequest r : open) {
                data.notifications().notify(r.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                        "'" + listing.getTitle() + "' was marked as sold, so your offer was declined.",
                        ref);
            }
            listing.setStatus(ListingStatus.SOLD);
            data.listings().update(listing);
        });
    }

    public LinkedHashMap<Listing, List<DealRequest>> pendingRequestsByListing(User actor) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        List<DealRequest> all = data.deals().allRequests();
        Comparator<DealRequest> newestFirst = newestFirst(all);

        Map<String, Listing> listingsById = new HashMap<>();
        Map<String, List<DealRequest>> byListingId = new HashMap<>();
        for (DealRequest request : all) {
            Listing listing = request.getListing();
            if (!isOpen(request) || !isAvailable(listing)
                    || !seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
                continue;
            }
            String id = listing.getId();
            listingsById.putIfAbsent(id, listing);
            byListingId.computeIfAbsent(id, key -> new ArrayList<>()).add(request);
        }
        byListingId.values().forEach(group -> group.sort(newestFirst));

        List<String> orderedIds = new ArrayList<>(byListingId.keySet());
        orderedIds.sort((left, right) -> newestFirst.compare(
                byListingId.get(left).get(0), byListingId.get(right).get(0)));

        LinkedHashMap<Listing, List<DealRequest>> result = new LinkedHashMap<>();
        for (String id : orderedIds) {
            result.put(listingsById.get(id), List.copyOf(byListingId.get(id)));
        }
        return result;
    }

    public List<DealRequest> pendingRequestsForListing(User actor, Listing listing) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(listing, "listing");
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own listing " + listing.getId());
        }
        if (!isAvailable(listing)) {
            return List.of();
        }
        List<DealRequest> group = new ArrayList<>();
        for (DealRequest request : data.deals().lifecycle().requestsFor(listing)) {
            if (isOpen(request)) {
                group.add(request);
            }
        }
        group.sort(newestFirst(data.deals().allRequests()));
        return List.copyOf(group);
    }

    private static Comparator<DealRequest> newestFirst(List<DealRequest> all) {
        Map<String, Integer> submitOrder = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            submitOrder.put(all.get(i).getId(), i);
        }
        return Comparator.comparing(DealRequest::getSubmittedTime)
                .thenComparingInt(request -> submitOrder.getOrDefault(request.getId(), -1))
                .reversed();
    }

    private static boolean isOpen(DealRequest request) {
        RequestStatus status = request.getStatus();
        return status == RequestStatus.PENDING || status == RequestStatus.COUNTERED;
    }

    private static boolean isAvailable(Listing listing) {
        if (listing.isDeleted()) {
            return false;
        }
        ListingStatus status = listing.getStatus();
        return status == ListingStatus.LIVE || status == ListingStatus.PENDING_REVIEW
                || status == ListingStatus.REJECTED;
    }
}
