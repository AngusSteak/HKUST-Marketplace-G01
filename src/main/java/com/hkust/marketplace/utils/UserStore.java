package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** In-memory accounts, keyed by lower-case username. */
public class UserStore {

    private final Map<String, User> users = new LinkedHashMap<>();

    public boolean usernameExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public User find(String username) {
        return users.get(username.toLowerCase());
    }

    public void register(User user) {
        // Usernames are unique across roles (ADR-0003): first registration wins, so a
        // duplicate can never replace the existing account.
        users.putIfAbsent(user.getUsername().toLowerCase(), user);
    }

    public Collection<User> all() {
        return users.values();
    }

    public List<User> byRole(UserRole role) {
        return users.values().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    void seed() {
        Admin admin = new Admin("admin", "System Admin", PasswordHasher.hash("admin123"), UserRole.ADMIN);
        admin.setFullName("HKUST Marketplace Admin");
        users.put("admin", admin);

        Buyer buyer1 = new Buyer("alice", "Alice Chan", PasswordHasher.hash("password1"), UserRole.BUYER);
        buyer1.setResidenceHall("Hall 3");
        users.put("alice", buyer1);

        Buyer buyer2 = new Buyer("bob", "Bob Wong", PasswordHasher.hash("password1"), UserRole.BUYER);
        buyer2.setResidenceHall("Off-campus");
        users.put("bob", buyer2);

        Seller seller1 = new Seller("charlie", "Charlie Lee", PasswordHasher.hash("password1"), UserRole.SELLER);
        seller1.setBio("Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup("Library");
        users.put("charlie", seller1);

        Seller seller2 = new Seller("diana", "Diana Ng", PasswordHasher.hash("password1"), UserRole.SELLER);
        seller2.setBio("Selling furniture and kitchen items before graduation.");
        seller2.setPreferredMeetup("Hall 5 Lobby");
        users.put("diana", seller2);
    }

    // Password strength helper (demo)
    public static String checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) return "Password cannot be empty.";
        if (password.length() < 8) return "Password must be at least 8 characters.";
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            return "Password should contain uppercase, lowercase and a digit.";
        }
        return "OK";
    }
}
