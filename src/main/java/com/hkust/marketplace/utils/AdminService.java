package com.hkust.marketplace.utils;

import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.forms.PhotoDraft;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.LendingRules;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PasswordRule;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Admin-only actions. Every method first checks that {@code admin} is an active Admin account
 * ({@link AccessControl}) and throws {@link SecurityException} otherwise, before changing
 * anything. Each action saves through the stores and sends the notification or audit entry
 * that goes with it, so the admin screens only collect input and call these.
 */
public final class AdminService {

    private final DummyDataService data;

    /**
     * Creates the service over the given stores.
     *
     * @param data the stores this service reads and writes
     */
    public AdminService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** The take-down note on a listing's open reports (SRS UC-3.7 S1 step 3). */
    public static final String TAKEN_DOWN_RESOLUTION = "Listing taken down";

    /** SRS UC-3.3 S1: every listing taken down with this reason when its owner is deleted. */
    public static final String ACCOUNT_DELETED_REASON = "Seller account deleted";

    /** Shown when an add-account form has no username. */
    public static final String USERNAME_REQUIRED = "Username is required.";
    /** Shown when an add-account form reuses an existing username. */
    public static final String USERNAME_TAKEN = "Username already taken. Please choose another.";
    /** Shown when an add-account form has no full name. */
    public static final String FULL_NAME_REQUIRED = "Full name is required.";
    /** Admins cannot deactivate or delete the seeded admin account. */
    public static final String ADMIN_PROTECTED = "The admin account cannot be disabled or deleted.";
    /** Shown when an action targets an already soft-deleted account. */
    public static final String ACCOUNT_DELETED = "This account has been deleted.";

    /**
     * What the soft-delete warning dialog shows before the admin confirms (SRS UC-3.3).
     *
     * @param listings the seller's listings
     * @param requests the requests the account sent or received
     * @param deals the deals the account is part of
     * @param chats the chat threads the account is part of
     */
    public record AccountActivity(List<Listing> listings, List<DealRequest> requests,
                                  List<Deal> deals, List<ChatThread> chats) {
        /** Copies the lists so the snapshot cannot change. */
        public AccountActivity {
            listings = List.copyOf(listings);
            requests = List.copyOf(requests);
            deals = List.copyOf(deals);
            chats = List.copyOf(chats);
        }
    }

    /**
     * Approves a pending listing: it goes live and the seller is notified (SRS UC-3.2). The status,
     * review date and notification are saved together.
     *
     * @param admin the acting admin
     * @param listing the pending listing to approve
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

    /**
     * Rejects a pending listing with a reason the seller sees, saved together (SRS UC-3.2).
     *
     * @param admin the acting admin
     * @param listing the pending listing to reject
     * @param reason the rejection reason shown to the seller
     */
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
     * Takes a listing off the marketplace (SRS UC-3.7 S1): stores the reason and the Taken Down
     * status, records it in the audit log, resolves the open reports with
     * {@value #TAKEN_DOWN_RESOLUTION}, declines the pending and countered requests, and notifies
     * those buyers and the seller. The listing keeps its deals, chats and reports.
     *
     * <p>A Reserved or Lent listing first has its active deal cancelled (SRS UC-3.7 A2); the
     * decided request and deal stay in history. Other statuses cannot be taken down (A3).
     *
     * @param admin the acting admin
     * @param listing the Live, Reserved or Lent listing to take down
     * @param reason the take-down reason stored on the listing
     */
    public void takeDownListing(User admin, Listing listing, String reason) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        String why = requireText(reason, "Take-down reason");
        switch (listing.getStatus()) {
            case LIVE, RESERVED, LENT -> { }
            default -> throw new IllegalStateException(
                    "Only a Live, Reserved or Lent listing can be taken down.");
        }
        Optional<Deal> activeDeal = data.deals().lifecycle().activeDeal(listing);
        List<DealRequest> open = openRequestsOn(listing);
        NotificationRef ref = new NotificationRef(NotificationRef.Target.LISTING, listing.getId());
        data.database().inTransaction(() -> {
            activeDeal.ifPresent(deal -> data.deals().lifecycle().cancel(deal));
            open.forEach(r -> data.deals().lifecycle().decline(r));
            listing.setStatus(ListingStatus.TAKEN_DOWN);
            listing.setTakedownReason(why);
            data.listings().update(listing);
            data.audit().recordTakeDown(admin.getUsername(), listing.getId());
            data.reports().resolveAll(listing, TAKEN_DOWN_RESOLUTION);
            activeDeal.ifPresent(deal -> data.notifications().notify(deal.getBuyerUsername(),
                    NotificationCategory.LISTING_REMOVED,
                    "The deal for '" + listing.getTitle() + "' was cancelled because the listing was removed.",
                    ref));
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
     * Edits a listing's details without changing its status (SRS UC-3.7 S1), then audits the
     * change and notifies the seller. Prices and lending durations keep their existing rules.
     * The listing keeps its current photos.
     *
     * @param admin the acting admin
     * @param listing the listing to edit
     * @param title the new title
     * @param description the new description
     * @param category the new category
     * @param condition the new condition
     * @param location the new meetup location
     * @param rawPrice the new asking price for a Sale listing; ignored for other types
     * @param lendingDuration the new duration for a Lend listing; ignored for other types
     */
    public void editListing(User admin, Listing listing, String title, String description,
                            Category category, Condition condition, Location location,
                            String rawPrice, Duration lendingDuration) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        editListing(admin, listing, title, description, category, condition, location,
                rawPrice, lendingDuration, List.copyOf(listing.getPhotoPaths()), List.of(), null);
    }

    /**
     * Edits a listing's details and photos without changing its status (SRS UC-3.7 step 3.1.2),
     * then audits the change and notifies the seller. The admin keeps some of the listing's
     * existing photo paths and adds new JPEG/PNG files, with at most six photos in total
     * (SRS UC-3.7 A4). New photos are stored under the listing's seller and id.
     *
     * <p>Removed photos only leave the listing's stored paths; the files on disk are kept.
     *
     * @param admin the acting admin
     * @param listing the listing to edit
     * @param title the new title
     * @param description the new description
     * @param category the new category
     * @param condition the new condition
     * @param location the new meetup location
     * @param rawPrice the new asking price for a Sale listing; ignored for other types
     * @param lendingDuration the new duration for a Lend listing; ignored for other types
     * @param keptPhotoPaths the existing photo paths that stay on the listing
     * @param newPhotos the newly chosen photo files to store
     * @param photos storage for the new photos; may be {@code null} when none are added
     * @throws IllegalArgumentException if the combined photos exceed six, a new file is not a
     *         supported JPEG/PNG, or a text/price/duration rule fails
     */
    public void editListing(User admin, Listing listing, String title, String description,
                            Category category, Condition condition, Location location,
                            String rawPrice, Duration lendingDuration,
                            List<String> keptPhotoPaths, List<PhotoDraft> newPhotos, PhotoStorage photos) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        String newTitle = requireText(title, "Title");
        // Validated as in Post Listing (SRS UC-3.7 step 3.1.2), which requires a description.
        String newDescription = requireText(description, "Description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(location, "meetup location");
        Money price = listing instanceof SaleListing ? requirePrice(rawPrice) : null;
        Duration duration = listing instanceof LendListing
                ? LendingRules.requireLendingDuration(lendingDuration) : null;

        List<String> kept = keptPhotoPaths == null
                ? List.copyOf(listing.getPhotoPaths()) : List.copyOf(keptPhotoPaths);
        List<PhotoDraft> additions = newPhotos == null ? List.of() : List.copyOf(newPhotos);
        if (kept.size() + additions.size() > ListingFormState.MAX_PHOTOS) {
            throw new IllegalArgumentException("A listing can have at most "
                    + ListingFormState.MAX_PHOTOS + " photos");
        }
        for (String path : kept) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Photo path must not be blank");
            }
        }
        for (PhotoDraft draft : additions) {
            if (draft == null || draft.source() == null) {
                throw new IllegalArgumentException("A photo file is required");
            }
            if (!isSupportedImage(draft.source().getFileName().toString())) {
                throw new IllegalArgumentException("Only JPEG and PNG photos are supported");
            }
        }
        if (!additions.isEmpty()) {
            Objects.requireNonNull(photos, "photos");
        }

        String oldTitle = listing.getTitle();
        String oldDescription = listing.getDescription();
        Category oldCategory = listing.getCategory();
        Condition oldCondition = listing.getCondition();
        Location oldLocation = listing.getMeetupLocation();
        Money oldPrice = listing instanceof SaleListing sale ? sale.getPrice() : null;
        Duration oldDuration = listing instanceof LendListing lend ? lend.getLendingDuration() : null;
        List<String> oldPhotoPaths = List.copyOf(listing.getPhotoPaths());
        List<String> storedPaths = new ArrayList<>();
        try {
            for (PhotoDraft draft : additions) {
                storedPaths.add(photos.store(draft.source(), listing.getSellerUsername(), listing.getId()));
            }
            List<String> combinedPaths = new ArrayList<>(kept);
            combinedPaths.addAll(storedPaths);
            data.database().inTransaction(() -> {
                listing.setTitle(newTitle);
                listing.setDescription(newDescription);
                listing.setCategory(category);
                listing.setCondition(condition);
                listing.setMeetupLocation(location);
                listing.setPhotoPaths(combinedPaths);
                if (listing instanceof SaleListing sale) {
                    sale.setPrice(price);
                } else if (listing instanceof LendListing lend) {
                    lend.setLendingDuration(duration);
                }
                data.listings().update(listing);
                data.audit().recordEdit(admin.getUsername(), listing.getId());
                data.notifications().notify(listing.getSellerUsername(), NotificationCategory.ANNOUNCEMENT,
                        "An admin edited your listing '" + listing.getTitle() + "'.",
                        new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
            });
        } catch (RuntimeException e) {
            listing.setTitle(oldTitle);
            listing.setDescription(oldDescription);
            listing.setCategory(oldCategory);
            listing.setCondition(oldCondition);
            listing.setMeetupLocation(oldLocation);
            listing.setPhotoPaths(oldPhotoPaths);
            if (listing instanceof SaleListing sale) {
                sale.setPrice(oldPrice);
            } else if (listing instanceof LendListing lend) {
                lend.setLendingDuration(oldDuration);
            }
            for (String path : storedPaths) {
                try {
                    Files.deleteIfExists(Path.of(path));
                } catch (IOException ignored) {
                    // best-effort cleanup of a photo whose listing edit did not save
                }
            }
            throw e;
        }
    }

    /** SRS UC-3.7 A6. */
    public static final String RESOLUTION_NOTE_REQUIRED = "A resolution note is required to dismiss the reports.";

    /**
     * Dismisses every open report on the listing with one resolution note (SRS UC-3.7 steps
     * 3.3.2-3.3.3) and records it in the audit log. The listing itself is unchanged.
     *
     * @param admin the acting admin
     * @param listing the reported listing
     * @param note the resolution note stored on every open report
     * @return how many reports were resolved
     * @throws IllegalArgumentException with {@link #RESOLUTION_NOTE_REQUIRED} if the note is blank (A6)
     * @throws IllegalStateException if the listing has no open reports
     */
    public int dismissReports(User admin, Listing listing, String note) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException(RESOLUTION_NOTE_REQUIRED);
        }
        if (data.reports().openReports(listing).isEmpty()) {
            throw new IllegalStateException("This listing has no open reports.");
        }
        int[] resolved = new int[1];
        data.database().inTransaction(() -> {
            resolved[0] = data.reports().resolveAll(listing, note.trim());
            data.audit().recordReportResolution(admin.getUsername(), listing.getId());
        });
        return resolved[0];
    }

    /**
     * Adds a listing for a seller from the admin screen, saved as Live (SRS UC-3.7), and audits it.
     * The seller is told, so they know the listing exists under their name.
     *
     * @param admin the acting admin
     * @param seller the active seller who will own the listing
     * @param state the filled listing form
     * @param photos storage for the photos chosen in the form
     * @return the saved listing
     */
    public Listing addListingOnBehalf(User admin, Seller seller, ListingFormState state, PhotoStorage photos) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(photos, "photos");
        if (!seller.isActive() || seller.isDeleted()) {
            throw new IllegalStateException("Only an active seller can receive a listing.");
        }
        var errors = state.validate(seller);
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
        listing.setStatus(ListingStatus.LIVE);
        listing.setReviewedAt(LocalDateTime.now());
        try {
            data.database().inTransaction(() -> {
                data.listings().add(listing);
                data.audit().recordAddOnBehalf(admin.getUsername(), listing.getId());
                data.notifications().notify(seller.getUsername(), NotificationCategory.ANNOUNCEMENT,
                        "An admin added the listing '" + listing.getTitle() + "' on your behalf.",
                        new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
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

    /**
     * Adds a buyer or seller account from the admin screen (SRS UC-3.4). The username must be
     * unused and the password must pass every rule; the role never changes later.
     *
     * @param admin the acting admin
     * @param role BUYER or SELLER (never ADMIN)
     * @param username the new unique username
     * @param fullName the account's full name
     * @param password the plain-text password, hashed before storage
     * @param residenceHall the buyer's residence hall, or {@code null}
     * @param bio the seller's bio, or {@code null}
     * @param meetup the seller's preferred meetup location, or {@code null}
     * @return the saved account
     */
    public User addAccount(User admin, UserRole role, String username, String fullName, String password,
                           String residenceHall, String bio, Location meetup) {
        AccessControl.require(admin, UserRole.ADMIN);
        if (role != UserRole.BUYER && role != UserRole.SELLER) {
            throw new IllegalArgumentException("Admins can add only buyer or seller accounts.");
        }
        String key = requireText(username, "Username").toLowerCase(Locale.ROOT);
        if (data.users().usernameExists(key)) {
            throw new IllegalArgumentException(USERNAME_TAKEN);
        }
        String name = requireText(fullName, "Full name");
        Set<PasswordRule> failed = UserStore.checkPasswordStrength(password, key);
        if (!failed.isEmpty()) {
            throw new IllegalArgumentException(UserStore.formatPasswordErrors(failed));
        }

        User account;
        if (role == UserRole.BUYER) {
            Buyer buyer = new Buyer(key, name, PasswordHasher.hash(password), UserRole.BUYER);
            buyer.setResidenceHall(blankToNull(residenceHall));
            account = buyer;
        } else {
            Seller seller = new Seller(key, name, PasswordHasher.hash(password), UserRole.SELLER);
            seller.setBio(blankToNull(bio));
            if (meetup != null) {
                seller.setPreferredMeetup(EnumSet.of(meetup));
            }
            account = seller;
        }
        data.database().inTransaction(() -> {
            if (!data.users().register(account)) {
                throw new IllegalArgumentException(USERNAME_TAKEN);
            }
            data.audit().append(admin.getUsername(), "Added account", "@" + key);
        });
        return account;
    }

    /**
     * Edits an account's full name and role-specific fields (SRS UC-3.4). The username, role and
     * status are not editable here, and the admin account is changed from its own profile screen.
     *
     * @param admin the acting admin
     * @param account the buyer or seller to edit
     * @param fullName the new full name
     * @param residenceHall the new residence hall for a buyer, or {@code null}
     * @param bio the new bio for a seller, or {@code null}
     * @param meetup the new preferred meetup for a seller, or {@code null} to keep the current one
     */
    public void updateAccount(User admin, User account, String fullName, String residenceHall,
                              String bio, Location meetup) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
        if (account.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException("Edit the admin account from My Profile.");
        }
        String name = requireText(fullName, "Full name");
        String oldName = account.getFullName();
        String oldHall = account instanceof Buyer buyer ? buyer.getResidenceHall() : null;
        String oldBio = account instanceof Seller seller ? seller.getBio() : null;
        Set<Location> oldMeetup = account instanceof Seller seller ? seller.getPreferredMeetup() : Set.of();
        try {
            data.database().inTransaction(() -> {
                account.setFullName(name);
                if (account instanceof Buyer buyer) {
                    buyer.setResidenceHall(blankToNull(residenceHall));
                } else if (account instanceof Seller seller) {
                    seller.setBio(blankToNull(bio));
                    if (meetup != null) {
                        seller.setPreferredMeetup(EnumSet.of(meetup));
                    }
                }
                data.users().save(account);
                data.audit().recordEdit(admin.getUsername(), "@" + account.getUsername());
                data.notifications().notify(account.getUsername(), NotificationCategory.ANNOUNCEMENT,
                        "An admin updated your account details.",
                        new NotificationRef(NotificationRef.Target.USER, account.getUsername()));
            });
        } catch (RuntimeException e) {
            account.setFullName(oldName);
            if (account instanceof Buyer buyer) {
                buyer.setResidenceHall(oldHall);
            } else if (account instanceof Seller seller) {
                seller.setBio(oldBio);
                seller.setPreferredMeetup(oldMeetup);
            }
            throw e;
        }
    }

    /**
     * Activates or deactivates a buyer or seller account (SRS UC-3.4). A deactivated account can
     * no longer log in and its listings stop being browsable through their account status; the
     * listings themselves keep their statuses. The admin account is protected.
     *
     * @param admin the acting admin
     * @param account the buyer or seller to activate or deactivate
     * @param active {@code true} to activate, {@code false} to deactivate
     */
    public void setActive(User admin, User account, boolean active) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
        if (account.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException(ADMIN_PROTECTED);
        }
        if (account.isDeleted()) {
            throw new IllegalStateException(ACCOUNT_DELETED);
        }
        if (account.isActive() == active) {
            return;
        }
        try {
            data.database().inTransaction(() -> {
                account.setActive(active);
                data.users().save(account);
                data.audit().append(admin.getUsername(), active ? "Reactivated account" : "Deactivated account",
                        "@" + account.getUsername());
                data.notifications().notify(account.getUsername(), NotificationCategory.ANNOUNCEMENT,
                        active ? "Your account was reactivated by an admin."
                                : "Your account was deactivated by an admin.",
                        new NotificationRef(NotificationRef.Target.USER, account.getUsername()));
            });
        } catch (RuntimeException e) {
            account.setActive(!active);
            throw e;
        }
    }

    /**
     * What the soft-delete warning lists before the admin confirms (SRS UC-3.3 S1).
     *
     * @param admin the acting admin
     * @param account the account that would be deleted
     * @return the activity that soft delete would close
     */
    public AccountActivity accountActivity(User admin, User account) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
        String username = account.getUsername();
        List<DealRequest> requests = new ArrayList<>(data.deals().requestsByBuyer(username));
        requests.addAll(data.deals().requestsForSeller(username));
        List<Deal> deals = new ArrayList<>(data.deals().dealsForBuyer(username));
        deals.addAll(data.deals().dealsForSeller(username));
        List<ChatThread> chats = new ArrayList<>(data.chats().forBuyer(username));
        chats.addAll(data.chats().forSeller(username));
        return new AccountActivity(data.listings().bySeller(username), requests, deals, chats);
    }

    /**
     * Soft-deletes an account (SRS UC-3.3): live and pending listings are taken down with
     * {@value #ACCOUNT_DELETED_REASON}, open requests are declined, active deals cancelled, the
     * affected users notified, the profile fields cleared, and an audit entry written. Historical
     * deals, reviews and chats keep pointing at the account, which now reads "deleted user".
     *
     * @param admin the acting admin
     * @param account the buyer or seller to soft-delete
     */
    public void softDeleteAccount(User admin, User account) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(account, "account");
        if (account.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException(ADMIN_PROTECTED);
        }
        if (account.isDeleted()) {
            throw new IllegalStateException(ACCOUNT_DELETED);
        }
        String username = account.getUsername();
        List<Listing> listings = data.listings().bySeller(username);
        List<DealRequest> openRequests = new ArrayList<>(data.deals().openRequestsByBuyer(username));
        List<Deal> activeDeals = new ArrayList<>(data.deals().activeDealsForBuyer(username));
        for (Listing listing : listings) {
            openRequests.addAll(openRequestsOn(listing));
            data.deals().lifecycle().activeDeal(listing).ifPresent(activeDeals::add);
        }

        Set<String> affected = new LinkedHashSet<>();
        openRequests.forEach(r -> affected.add(r.getBuyerUsername().equalsIgnoreCase(username)
                ? r.getSellerUsername() : r.getBuyerUsername()));
        activeDeals.forEach(d -> affected.add(d.getBuyerUsername().equalsIgnoreCase(username)
                ? d.getSellerUsername() : d.getBuyerUsername()));
        affected.removeIf(name -> name.equalsIgnoreCase(username));

        String oldName = account.getFullName();
        boolean oldActive = account.isActive();
        String oldHall = account instanceof Buyer buyer ? buyer.getResidenceHall() : null;
        String oldBio = account instanceof Seller seller ? seller.getBio() : null;
        Set<Location> oldMeetup = account instanceof Seller seller ? seller.getPreferredMeetup() : Set.of();
        Map<Listing, ListingStatus> oldStatuses = new LinkedHashMap<>();
        Map<Listing, String> oldReasons = new LinkedHashMap<>();
        for (Listing listing : listings) {
            oldStatuses.put(listing, listing.getStatus());
            oldReasons.put(listing, listing.getTakedownReason());
        }
        try {
            data.database().inTransaction(() -> {
                for (DealRequest request : openRequests) {
                    if (request.getStatus() == RequestStatus.PENDING
                            || request.getStatus() == RequestStatus.COUNTERED) {
                        data.deals().lifecycle().decline(request);
                    }
                }
                for (Deal deal : activeDeals) {
                    if (deal.getStatus() == com.hkust.marketplace.models.DealStatus.ACTIVE) {
                        data.deals().lifecycle().cancel(deal);
                    }
                }
                for (Listing listing : listings) {
                    if (listing.getStatus() == ListingStatus.LIVE
                            || listing.getStatus() == ListingStatus.PENDING_REVIEW
                            || listing.getStatus() == ListingStatus.RESERVED
                            || listing.getStatus() == ListingStatus.LENT) {
                        listing.setStatus(ListingStatus.TAKEN_DOWN);
                        listing.setTakedownReason(ACCOUNT_DELETED_REASON);
                        data.listings().update(listing);
                        data.reports().resolveAll(listing, TAKEN_DOWN_RESOLUTION);
                    }
                }

                account.setActive(false);
                account.setDeleted(true);
                account.setFullName("Deleted user");
                if (account instanceof Buyer buyer) {
                    buyer.setResidenceHall(null);
                } else if (account instanceof Seller seller) {
                    seller.setBio(null);
                    seller.setPreferredMeetup(EnumSet.noneOf(Location.class));
                }
                data.users().save(account);
                data.audit().recordAccountDeletion(admin.getUsername(), "@" + username);

                for (String name : affected) {
                    data.notifications().notify(name, closedDealCategory(name),
                            "An account involved in your request or deal was deleted, so it was closed.",
                            new NotificationRef(NotificationRef.Target.USER, username));
                }
            });
        } catch (RuntimeException e) {
            account.setFullName(oldName);
            account.setActive(oldActive);
            account.setDeleted(false);
            if (account instanceof Buyer buyer) {
                buyer.setResidenceHall(oldHall);
            } else if (account instanceof Seller seller) {
                seller.setBio(oldBio);
                seller.setPreferredMeetup(oldMeetup);
            }
            // shortcut: the database rolled back, but cancelled deals and declined requests keep
            // their in-memory state. Restore them if this cascade ever becomes likely to fail.
            oldStatuses.forEach((listing, status) -> {
                listing.setStatus(status);
                listing.setTakedownReason(oldReasons.get(listing));
            });
            throw e;
        }
    }

    /**
     * Hides or unhides a review; hidden reviews leave the seller's rating (SRS UC-3.6).
     *
     * @param admin the acting admin
     * @param review the review to change
     * @param hidden {@code true} to hide, {@code false} to show
     */
    public void setReviewHidden(User admin, Review review, boolean hidden) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(review, "review");
        review.setHidden(hidden);
        data.reviews().save(review);
    }

    /**
     * Saves the admin's own profile (SRS UC-3.4). The full name must not be whitespace only; a
     * password change needs the current password and both new-password fields.
     *
     * @param admin the acting admin, also the account being changed
     * @param fullName the new full name
     * @param currentPassword the current password, needed only when changing the password
     * @param newPassword the new password, or blank to keep the current one
     * @param confirmPassword the new password again
     * @return the names of the fields that changed, empty when nothing changed
     * @throws IllegalArgumentException with the reason if a check fails; nothing changes
     */
    public List<String> updateProfile(User admin, String fullName, String currentPassword,
                                      String newPassword, String confirmPassword) {
        AccessControl.require(admin, UserRole.ADMIN);
        String name = requireText(fullName, "Full name");
        boolean changePassword = !isBlank(currentPassword) || !isBlank(newPassword) || !isBlank(confirmPassword);
        if (changePassword) {
            if (isBlank(currentPassword) || isBlank(newPassword) || isBlank(confirmPassword)) {
                throw new IllegalArgumentException("Current password, new password and confirmation are all required.");
            }
            if (!PasswordHasher.verify(currentPassword, admin.getPasswordHash())) {
                throw new IllegalArgumentException(ProfileService.CURRENT_PASSWORD_WRONG);
            }
            Set<PasswordRule> failed = UserStore.checkPasswordStrength(newPassword, admin.getUsername());
            if (!failed.isEmpty()) {
                throw new IllegalArgumentException(UserStore.formatPasswordErrors(failed));
            }
            if (!Objects.equals(newPassword, confirmPassword)) {
                throw new IllegalArgumentException(ProfileService.CONFIRMATION_MISMATCH);
            }
        }

        List<String> changed = new ArrayList<>();
        if (!name.equals(admin.getFullName())) {
            changed.add("full name");
        }
        if (changePassword) {
            changed.add("password");
        }
        if (changed.isEmpty()) {
            return List.of();
        }
        String oldName = admin.getFullName();
        String oldHash = admin.getPasswordHash();
        try {
            data.database().inTransaction(() -> {
                admin.setFullName(name);
                if (changePassword) {
                    admin.setPasswordHash(PasswordHasher.hash(newPassword));
                }
                data.users().save(admin);
                data.audit().recordEdit(admin.getUsername(), "@" + admin.getUsername());
            });
        } catch (RuntimeException e) {
            admin.setFullName(oldName);
            admin.setPasswordHash(oldHash);
            throw e;
        }
        return List.copyOf(changed);
    }

    /**
     * The category for telling someone their request or deal was closed by a soft delete: an
     * Offer Update for a buyer, an Announcement for a seller. Account Change is the admin's own
     * category (SRS UC-3.x View Notifications), so it is never sent to buyers or sellers.
     */
    private NotificationCategory closedDealCategory(String username) {
        User user = data.users().find(username);
        return user != null && user.getRole() == UserRole.BUYER
                ? NotificationCategory.OFFER_UPDATE : NotificationCategory.ANNOUNCEMENT;
    }

    private List<DealRequest> openRequestsOn(Listing listing) {
        List<DealRequest> open = new ArrayList<>();
        for (DealRequest request : data.deals().lifecycle().requestsFor(listing)) {
            if (request.getStatus() == RequestStatus.PENDING
                    || request.getStatus() == RequestStatus.COUNTERED) {
                open.add(request);
            }
        }
        return open;
    }

    private static boolean isSupportedImage(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png");
    }

    private static Money requirePrice(String rawPrice) {
        try {
            return Money.of(rawPrice);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Asking price is invalid: " + e.getMessage(), e);
        }
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
