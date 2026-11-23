package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.PasswordRule;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Accounts, keyed by lower-case username. Accounts are kept in memory and every change is
 * written to the {@code users} table and loaded back at startup ({@link #load()}). They
 * survive a restart once the reset-on-start switch is turned off (#59).
 *
 * <p>Change an account's fields, then call {@link #save(User)}; never change a {@code User}
 * and skip the store.
 */
public class UserStore {

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Database db;

    UserStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public boolean usernameExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public User find(String username) {
        return users.get(username.toLowerCase());
    }

    /**
     * Adds a new account and saves it. Usernames are unique across roles (ADR-0003): first
     * registration wins, so a duplicate can never replace the existing account.
     *
     * @return false if the username was already taken (nothing is saved)
     */
    public boolean register(User user) {
        String key = user.getUsername().toLowerCase();
        if (users.containsKey(key)) {
            return false;
        }
        db.inTransaction(() -> {
            db.update("INSERT INTO users (username, full_name, password_hash, role, active, deleted,"
                            + " created_at, residence_hall, bio) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    key, user.getFullName(), user.getPasswordHash(), user.getRole(), user.isActive(),
                    user.isDeleted(), user.getCreatedAt(), residenceHall(user), bio(user));
            saveMeetupLocations(key, user);
        });
        users.put(key, user);
        return true;
    }

    /**
     * Saves every changeable field of an existing account: full name, password hash, active,
     * deleted, residence hall, bio and preferred meet-up locations. The username and role
     * never change.
     */
    public void save(User user) {
        String key = user.getUsername().toLowerCase();
        if (users.get(key) != user) {
            throw new IllegalArgumentException("Unknown account: " + user.getUsername());
        }
        db.inTransaction(() -> {
            db.update("UPDATE users SET full_name = ?, password_hash = ?, active = ?, deleted = ?,"
                            + " residence_hall = ?, bio = ? WHERE username = ?",
                    user.getFullName(), user.getPasswordHash(), user.isActive(), user.isDeleted(),
                    residenceHall(user), bio(user), key);
            db.update("DELETE FROM seller_meetup_locations WHERE username = ?", key);
            saveMeetupLocations(key, user);
        });
    }

    /** Reads every saved account back into memory. Called once at startup. */
    void load() {
        users.clear();
        for (User user : db.query("SELECT * FROM users ORDER BY rowid", UserStore::toUser)) {
            users.put(user.getUsername(), user);
        }
        for (String[] row : db.query("SELECT username, location FROM seller_meetup_locations",
                rows -> new String[]{rows.getString("username"), rows.getString("location")})) {
            if (users.get(row[0]) instanceof Seller seller) {
                Set<Location> locations = EnumSet.noneOf(Location.class);
                locations.addAll(seller.getPreferredMeetup());
                locations.add(Location.valueOf(row[1]));
                seller.setPreferredMeetup(locations);
            }
        }
    }

    private static User toUser(ResultSet row) throws SQLException {
        String username = row.getString("username");
        String fullName = row.getString("full_name");
        String hash = row.getString("password_hash");
        UserRole role = UserRole.valueOf(row.getString("role"));
        User user = switch (role) {
            case ADMIN -> new Admin(username, fullName, hash, role);
            case BUYER -> {
                Buyer buyer = new Buyer(username, fullName, hash, role);
                buyer.setResidenceHall(row.getString("residence_hall"));
                yield buyer;
            }
            case SELLER -> {
                Seller seller = new Seller(username, fullName, hash, role);
                seller.setBio(row.getString("bio"));
                yield seller;
            }
        };
        user.setActive(Database.bool(row, "active"));
        user.setDeleted(Database.bool(row, "deleted"));
        user.setCreatedAt(Database.dateTime(row, "created_at"));
        return user;
    }

    private void saveMeetupLocations(String key, User user) {
        if (user instanceof Seller seller) {
            for (Location location : seller.getPreferredMeetup()) {
                db.update("INSERT INTO seller_meetup_locations (username, location) VALUES (?, ?)",
                        key, location);
            }
        }
    }

    private static String residenceHall(User user) {
        return user instanceof Buyer buyer ? buyer.getResidenceHall() : null;
    }

    private static String bio(User user) {
        return user instanceof Seller seller ? seller.getBio() : null;
    }

    /** Active and disabled accounts only; soft-deleted accounts stay in {@link #find}. */
    public Collection<User> all() {
        return users.values().stream()
                .filter(u -> !u.isDeleted())
                .collect(Collectors.toList());
    }

    public List<User> byRole(UserRole role) {
        return all().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    /** Label for a username stored in a historical record (review, deal, chat). */
    public String historicalLabel(String username) {
        User user = find(username);
        return user != null && user.isDeleted() ? "deleted user" : "@" + username;
    }

    void seed() {
        Admin admin = new Admin("admin", "System Admin", PasswordHasher.hash("admin123"), UserRole.ADMIN);
        admin.setFullName("HKUST Marketplace Admin");
        register(admin);

        Buyer buyer1 = new Buyer("alice", "Alice Chan", PasswordHasher.hash("password1"), UserRole.BUYER);
        buyer1.setResidenceHall("Hall 3");
        register(buyer1);

        Buyer buyer2 = new Buyer("bob", "Bob Wong", PasswordHasher.hash("password1"), UserRole.BUYER);
        buyer2.setResidenceHall("Off-campus");
        register(buyer2);

        Seller seller1 = new Seller("charlie", "Charlie Lee", PasswordHasher.hash("password1"), UserRole.SELLER);
        seller1.setBio("Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup(EnumSet.of(Location.LIBRARY));
        register(seller1);

        Seller seller2 = new Seller("diana", "Diana Ng", PasswordHasher.hash("password1"), UserRole.SELLER);
        seller2.setBio("Selling furniture and kitchen items before graduation.");
        seller2.setPreferredMeetup(EnumSet.of(Location.UG_HALL_V));
        register(seller2);
    }

    /**
     * Checks a password against UC-1.1 A3. Returns every rule that failed, so the caller
     * can report all problems at once; an empty set means the password is acceptable.
     */
    public static Set<PasswordRule> checkPasswordStrength(String password, String username) {
        String pwd = password == null ? "" : password;
        EnumSet<PasswordRule> failed = EnumSet.noneOf(PasswordRule.class);

        if (pwd.length() < 8 || pwd.length() > 64) failed.add(PasswordRule.LENGTH);
        if (pwd.chars().noneMatch(Character::isUpperCase)) failed.add(PasswordRule.UPPERCASE);
        if (pwd.chars().noneMatch(Character::isLowerCase)) failed.add(PasswordRule.LOWERCASE);
        if (pwd.chars().noneMatch(Character::isDigit)) failed.add(PasswordRule.DIGIT);
        if (pwd.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            failed.add(PasswordRule.SPECIAL);
        }
        if (pwd.chars().anyMatch(Character::isWhitespace)) failed.add(PasswordRule.NO_SPACES);
        if (username != null && pwd.equalsIgnoreCase(username)) failed.add(PasswordRule.NOT_USERNAME);

        return failed;
    }

    /** Human-readable, single-message form for the registration forms. */
    public static String formatPasswordErrors(Set<PasswordRule> failed) {
        return failed.stream()
                .map(PasswordRule::getMessage)
                .collect(Collectors.joining(" "));
    }
}
