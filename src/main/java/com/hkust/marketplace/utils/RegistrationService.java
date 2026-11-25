package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.DatabaseException;

import java.util.Objects;

/**
 * Buyer and seller registration (SRS UC-1.1, UC-2.1): saves the new account and tells every
 * admin about it (step 7, an Account Change notification). The registration screens validate
 * the form first; this class only stores the result.
 */
public final class RegistrationService {

    private final DummyDataService data;

    public RegistrationService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Registers a buyer or seller account.
     *
     * @return {@code false} if the username is already taken by any account (nothing changes)
     * @throws IllegalArgumentException for an admin account; there is no admin registration
     */
    public boolean register(User account) {
        Objects.requireNonNull(account, "account");
        if (account.getRole() != UserRole.BUYER && account.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("Only buyer and seller accounts can register.");
        }
        if (!data.users().register(account)) {
            return false;
        }
        String role = account.getRole() == UserRole.SELLER ? "seller" : "buyer";
        String message = "New " + role + " account registered: " + account.getFullName()
                + " (@" + account.getUsername() + ").";
        NotificationRef ref = new NotificationRef(NotificationRef.Target.USER, account.getUsername());
        try {
            for (User admin : data.users().byRole(UserRole.ADMIN)) {
                data.notifications().notify(admin.getUsername(), NotificationCategory.ACCOUNT_CHANGE, message, ref);
            }
        } catch (DatabaseException e) {
            // The account is saved and can log in; a missed admin notice must not make the new
            // user think registration failed and try again with a taken username.
            System.err.println("[RegistrationService] admin notice for @" + account.getUsername()
                    + " not saved: " + e.getMessage());
        }
        return true;
    }
}
