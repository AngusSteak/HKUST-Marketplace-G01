package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PasswordRule;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manage Profile (SRS UC-1.7): a signed-in buyer or seller changes their full name and,
 * optionally, their password. Every check runs before anything changes; on success the account
 * is saved and the admin is told which fields changed, never the password itself.
 */
public final class ProfileService {

    /** Shown when the new full name is blank. */
    public static final String NAME_REQUIRED = "Full name is required.";
    /** Shown when the current password does not match. */
    public static final String CURRENT_PASSWORD_WRONG = "Current password is incorrect.";
    /** Shown when the new password and confirmation differ. */
    public static final String CONFIRMATION_MISMATCH = "New password and confirmation do not match.";
    /** Longest accepted seller bio. */
    public static final int BIO_MAX_LENGTH = 500;
    /** Shown when the seller bio is too long. */
    public static final String BIO_TOO_LONG = "Bio must be 500 characters or fewer.";

    private final DummyDataService data;

    /** Creates the service over the given stores. */
    public ProfileService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Saves the profile. The password is changed only if any of the three password fields is
     * filled in; then all three are checked.
     *
     * @return the fields that changed ("full name", "password"), empty if nothing changed
     * @throws SecurityException if {@code actor} is not an active buyer or seller
     * @throws IllegalArgumentException with the reason if a check fails (A1, A2); nothing changes
     */
    public List<String> updateProfile(User actor, String fullName, String currentPassword,
                                      String newPassword, String confirmPassword) {
        AccessControl.require(actor, UserRole.BUYER, UserRole.SELLER);
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(NAME_REQUIRED);
        }
        boolean changePassword = !isBlank(currentPassword) || !isBlank(newPassword) || !isBlank(confirmPassword);
        if (changePassword) {
            checkPasswordChange(actor, currentPassword, newPassword, confirmPassword);
        }

        List<String> changed = new ArrayList<>();
        if (!name.equals(actor.getFullName())) {
            changed.add("full name");
        }
        if (changePassword) {
            changed.add("password");
        }
        if (changed.isEmpty()) {
            return List.of();
        }

        String oldName = actor.getFullName();
        String oldHash = actor.getPasswordHash();
        try {
            data.database().inTransaction(() -> {
                actor.setFullName(name);
                if (changePassword) {
                    actor.setPasswordHash(PasswordHasher.hash(newPassword));
                }
                data.users().save(actor);
                notifyAdmins(actor, changed);
            });
        } catch (RuntimeException e) {
            // The database rolled back; put the account object back the way it was too.
            actor.setFullName(oldName);
            actor.setPasswordHash(oldHash);
            throw e;
        }
        return List.copyOf(changed);
    }

    /**
     * SRS UC-2.6: a seller changes their full name, bio, preferred meet-up locations and,
     * optionally, their password. Every check runs before anything changes; on success the
     * account is saved in one transaction and every admin is told which fields changed, never
     * the password itself.
     *
     * @return the fields that changed in a fixed order, empty if nothing changed
     * @throws SecurityException if {@code actor} is not an active seller
     * @throws IllegalArgumentException with the reason if a check fails; nothing changes
     */
    public List<String> updateSellerProfile(User actor, String fullName, String bio, Set<Location> meetup,
                                            String currentPassword, String newPassword, String confirmPassword) {
        AccessControl.require(actor, UserRole.SELLER);
        if (!(actor instanceof Seller seller)) {
            throw new SecurityException("@" + actor.getUsername() + " is not a seller");
        }

        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(NAME_REQUIRED);
        }
        String trimmedBio = bio == null ? "" : bio.trim();
        if (trimmedBio.isBlank()) {
            trimmedBio = null;
        } else if (trimmedBio.length() > BIO_MAX_LENGTH) {
            throw new IllegalArgumentException(BIO_TOO_LONG);
        }
        final String normalizedBio = trimmedBio;
        // EnumSet.copyOf throws on an empty set that is not already an EnumSet (e.g. Set.of()).
        Set<Location> meetups = meetup == null || meetup.isEmpty()
                ? EnumSet.noneOf(Location.class) : EnumSet.copyOf(meetup);

        boolean changePassword = !isBlank(currentPassword) || !isBlank(newPassword) || !isBlank(confirmPassword);
        if (changePassword) {
            checkPasswordChange(actor, currentPassword, newPassword, confirmPassword);
        }

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(name, actor.getFullName())) {
            changed.add("full name");
        }
        String currentBio = seller.getBio();
        String normalizedCurrentBio = currentBio == null || currentBio.isBlank() ? null : currentBio.trim();
        if (!Objects.equals(normalizedBio, normalizedCurrentBio)) {
            changed.add("bio");
        }
        if (!Objects.equals(meetups, seller.getPreferredMeetup())) {
            changed.add("preferred meet-up locations");
        }
        if (changePassword) {
            changed.add("password");
        }
        if (changed.isEmpty()) {
            return List.of();
        }

        String oldName = actor.getFullName();
        String oldHash = actor.getPasswordHash();
        String oldBio = seller.getBio();
        Set<Location> oldMeetup = seller.getPreferredMeetup().isEmpty()
                ? EnumSet.noneOf(Location.class) : EnumSet.copyOf(seller.getPreferredMeetup());
        try {
            data.database().inTransaction(() -> {
                actor.setFullName(name);
                seller.setBio(normalizedBio);
                seller.setPreferredMeetup(meetups);
                if (changePassword) {
                    actor.setPasswordHash(PasswordHasher.hash(newPassword));
                }
                data.users().save(actor);
                notifyAdmins(actor, changed);
            });
        } catch (RuntimeException e) {
            // The database rolled back; put the account object back the way it was too.
            actor.setFullName(oldName);
            actor.setPasswordHash(oldHash);
            seller.setBio(oldBio);
            seller.setPreferredMeetup(oldMeetup);
            throw e;
        }
        return List.copyOf(changed);
    }

    private static void checkPasswordChange(User actor, String current, String newPassword, String confirm) {
        if (isBlank(current) || !PasswordHasher.verify(current, actor.getPasswordHash())) {
            throw new IllegalArgumentException(CURRENT_PASSWORD_WRONG);
        }
        Set<PasswordRule> failed = UserStore.checkPasswordStrength(newPassword, actor.getUsername());
        if (!failed.isEmpty()) {
            throw new IllegalArgumentException(UserStore.formatPasswordErrors(failed));
        }
        if (!Objects.equals(newPassword, confirm)) {
            throw new IllegalArgumentException(CONFIRMATION_MISMATCH);
        }
    }

    /** SRS UC-1.7 step 8: which fields changed, never the password itself. */
    private void notifyAdmins(User actor, List<String> changed) {
        String role = actor.getRole() == UserRole.SELLER ? "Seller" : "Buyer";
        String message = role + " @" + actor.getUsername() + " updated their profile: "
                + String.join(" and ", changed) + ".";
        NotificationRef ref = new NotificationRef(NotificationRef.Target.USER, actor.getUsername());
        for (User admin : data.users().byRole(UserRole.ADMIN)) {
            data.notifications().notify(admin.getUsername(), NotificationCategory.ACCOUNT_CHANGE, message, ref);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
