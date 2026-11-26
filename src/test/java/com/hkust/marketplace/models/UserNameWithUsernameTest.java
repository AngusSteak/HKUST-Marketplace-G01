package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Chats show the username beside the full name so people with the same name can be told apart. */
class UserNameWithUsernameTest {

    @Test
    void showsTheFullNameFollowedByTheUsername() {
        Buyer user = new Buyer("ikaj", "Ikaj", "hash", UserRole.BUYER);
        assertEquals("Ikaj @ikaj", user.getNameWithUsername());
    }

    @Test
    void twoUsersWithTheSameFullNameCanBeToldApart() {
        Buyer first = new Buyer("chris1", "Chris Wong", "hash", UserRole.BUYER);
        Buyer second = new Buyer("chris2", "Chris Wong", "hash", UserRole.BUYER);
        assertNotEquals(first.getNameWithUsername(), second.getNameWithUsername());
    }

    @Test
    void aDeletedUserShowsAsDeletedUser() {
        Buyer user = new Buyer("ikaj", "Ikaj", "hash", UserRole.BUYER);
        user.setDeleted(true);
        assertEquals("deleted user", user.getNameWithUsername());
    }
}
