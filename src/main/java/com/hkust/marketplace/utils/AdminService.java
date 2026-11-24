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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public AdminService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** The take-down note on a listing's open reports (SRS UC-3.7 S1 step 3). */
    public static final String TAKEN_DOWN_RESOLUTION = "Listing taken down";

    /** SRS UC-3.3 S1: every listing taken down with this reason when its owner is deleted. */
    public static final String ACCOUNT_DELETED_REASON = "Seller account deleted";

    public static final String USERNAME_REQUIRED = "Username is required.";
    public static final String USERNAME_TAKEN = "Username already taken. Please choose another.";
    public static final String FULL_NAME_REQUIRED = "Full name is required.";
    public static final String ADMIN_PROTECTED = "The admin account cannot be disabled or deleted.";
    public static final String ACCOUNT_DELETED = "This account has been deleted.";

    /** What the soft-delete warning dialog shows before the admin confirms (SRS UC-3.3). */
    public record AccountActivity(List<Listing> listings, List<DealRequest> requests,
                                  List<Deal> deals, List<ChatThread> chats) {
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
     * Takes a listing off the marketplace (SRS UC-3.7 S1): stores the reason and the Taken Down
     * status, records it in the audit log, resolves the open reports with
     * {@value #TAKEN_DOWN_RESOLUTION}, declines the pending and countered requests, and notifies
     * those buyers and the seller. The listing keeps its deals, chats and reports.
     *
     * <p>A Reserved or Lent listing first has its active deal cancelled (SRS UC-3.7 A2); the
     * decided request and deal stay in history. Other statuses cannot be taken down (A3).
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
     */
    public void editListing(User admin, Listing listing, String title, String description,
                            Category category, Condition condition, Location location,
                            String rawPrice, Duration lendingDuration) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(listing, "listing");
        String newTitle = requireText(title, "Title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(location, "meetup location");
        Money price = listing instanceof SaleListing ? requirePrice(rawPrice) : null;
        Duration duration = listing instanceof LendListing
                ? LendingRules.requireLendingDuration(lendingDuration) : null;

        String oldTitle = listing.getTitle();
        String oldDescription = listing.getDescription();
        Category oldCategory = listing.getCategory();
        Condition oldCondition = listing.getCondition();
        Location oldLocation = listing.getMeetupLocation();
        Money oldPrice = listing instanceof SaleListing sale ? sale.getPrice() : null;
        Duration oldDuration = listing instanceof LendListing lend ? lend.getLendingDuration() : null;
        try {
            data.database().inTransaction(() -> {
                listing.setTitle(newTitle);
                listing.setDescription(description == null ? "" : description.trim());
                listing.setCategory(category);
                listing.setCondition(condition);
                listing.setMeetupLocation(location);
                if (listing instanceof SaleListing sale) {
                    sale.setPrice(price);
                } else if (listing instanceof LendListing lend) {
                    lend.setLendingDuration(duration);
                }
                data.listings().update(listing);
                data.audit().recordEdit(admin.getUsername(), listing.getId());
                data.notifications().notify(listing.getSellerUsername(), NotificationCategory.ACCOUNT_CHANGE,
                        "An admin edited your listing '" + listing.getTitle() + "'.",
                        new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
            });
        } catch (RuntimeException e) {
            listing.setTitle(oldTitle);
            listing.setDescription(oldDescription);
            listing.setCategory(oldCategory);
            listing.setCondition(oldCondition);
            listing.setMeetupLocation(oldLocation);
            if (listing instanceof SaleListing sale) {
                sale.setPrice(oldPrice);
            } else if (listing instanceof LendListing lend) {
                lend.setLendingDuration(oldDuration);
            }
            throw e;
        }
    }

    /** Resolves one report as dismissed with the admin's note (SRS UC-3.7). */
    public void dismissReport(User admin, ListingReport report, String note) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(report, "report");
        String resolution = requireText(note, "Resolution note");
        data.database().inTransaction(() -> {
            data.reports().resolve(report, resolution);
            data.audit().recordReportResolution(admin.getUsername(), report.getId());
        });
    }

    /**
     * Adds a listing for a seller from the admin screen, saved as Live (SRS UC-3.7), and audits it.
     * The seller is told, so they know the listing exists under their name.
     */
    public Listing addListingOnBehalf(User admin, Seller seller, ListingFormState state, PhotoStorage photos) {
        AccessControl.require(admin, UserRole.ADMIN);
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(photos, "photos");
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
                data.notifications().notify(seller.getUsername(), NotificationCategory.ACCOUNT_CHANGE,
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
                data.notifications().notify(account.getUsername(), NotificationCategory.ACCOUNT_CHANGE,
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
                data.notifications().notify(account.getUsername(), NotificationCategory.ACCOUNT_CHANGE,
                        active ? "Your account was reactivated by an admin."
                                : "Your account was deactivated by an admin.",
                        new NotificationRef(NotificationRef.Target.USER, account.getUsername()));
            });
        } catch (RuntimeException e) {
            account.setActive(!active);
            throw e;
        }
    }

    /** What the soft-delete warning lists before the admin confirms (SRS UC-3.3 S1). */
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
                    data.notifications().notify(name, NotificationCategory.ACCOUNT_CHANGE,
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
            throw e;
        }
    }

    /** Hides or unhides a review; hidden reviews leave the seller's rating (SRS UC-3.6). */
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
