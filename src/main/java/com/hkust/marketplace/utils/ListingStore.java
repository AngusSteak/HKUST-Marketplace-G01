package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.persistence.Database;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Listings, kept in memory and written to the {@code listings} and {@code listing_photos}
 * tables on every change and loaded back at startup ({@link #load(UserStore)}). They survive
 * a restart once the reset-on-start switch is turned off (#59).
 *
 * <p>Change a listing's fields, then call {@link #update(Listing)}; never change a
 * {@code Listing} and skip the store.
 */
public class ListingStore {

    private static final int FIRST_ID = 100;

    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(FIRST_ID);
    private final Database db;

    ListingStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public Collection<Listing> all() {
        return listings.values();
    }

    public List<Listing> live() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .collect(Collectors.toList());
    }

    public List<Listing> pending() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.PENDING_REVIEW)
                .collect(Collectors.toList());
    }

    public List<Listing> bySeller(String username) {
        return listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public Listing find(String id) {
        return listings.get(id);
    }

    /** Adds a new listing (giving it the next {@code L...} id if it has none) and saves it. */
    public void add(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + idCounter.getAndIncrement());
        }
        db.inTransaction(() -> {
            db.update("INSERT INTO listings (id, type, seller_username, title, description, category,"
                            + " condition, meetup_location, status, submitted_date, reviewed_at,"
                            + " rejection_reason, takedown_reason, view_count, price,"
                            + " lending_duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    listing.getId(), listing.getType(), listing.getSellerUsername().toLowerCase(),
                    listing.getTitle(), listing.getDescription(), listing.getCategory(),
                    listing.getCondition(), listing.getMeetupLocation(), listing.getStatus(),
                    listing.getSubmittedDate(), listing.getReviewedAt(), listing.getRejectionReason(),
                    listing.getTakedownReason(), listing.getViewCount(), price(listing),
                    lendingDuration(listing));
            savePhotos(listing);
        });
        listings.put(listing.getId(), listing);
    }

    /**
     * Saves every field of an existing listing (status, reasons, dates, view count, details and
     * photos). Also accepts a new object for the same id, which then replaces the old one.
     */
    public void update(Listing listing) {
        if (!listings.containsKey(listing.getId())) {
            throw new IllegalArgumentException("Unknown listing: " + listing.getId());
        }
        db.inTransaction(() -> {
            db.update("UPDATE listings SET type = ?, seller_username = ?, title = ?, description = ?,"
                            + " category = ?, condition = ?, meetup_location = ?, status = ?,"
                            + " submitted_date = ?, reviewed_at = ?, rejection_reason = ?,"
                            + " takedown_reason = ?, view_count = ?, price = ?,"
                            + " lending_duration_seconds = ? WHERE id = ?",
                    listing.getType(), listing.getSellerUsername().toLowerCase(), listing.getTitle(),
                    listing.getDescription(), listing.getCategory(), listing.getCondition(),
                    listing.getMeetupLocation(), listing.getStatus(), listing.getSubmittedDate(),
                    listing.getReviewedAt(), listing.getRejectionReason(), listing.getTakedownReason(),
                    listing.getViewCount(), price(listing), lendingDuration(listing), listing.getId());
            db.update("DELETE FROM listing_photos WHERE listing_id = ?", listing.getId());
            savePhotos(listing);
        });
        listings.put(listing.getId(), listing);
    }

    /**
     * Deletes a listing and its photos, e.g. a seller withdrawing a listing nobody has acted on.
     * A listing with requests or deals (and, from #57/#58, chats or reports) cannot be deleted:
     * the database refuses, this throws {@link com.hkust.marketplace.persistence.DatabaseException}
     * and nothing changes. Take such a listing down by status instead (#26, #34).
     */
    public void remove(String id) {
        db.update("DELETE FROM listings WHERE id = ?", id);
        listings.remove(id);
    }

    /** Reads every saved listing back into memory, resolving sellers through {@code users}. */
    void load(UserStore users) {
        listings.clear();
        Map<String, List<String>> photos = new LinkedHashMap<>();
        for (String[] row : db.query("SELECT listing_id, path FROM listing_photos ORDER BY listing_id, position",
                rows -> new String[]{rows.getString("listing_id"), rows.getString("path")})) {
            photos.computeIfAbsent(row[0], k -> new ArrayList<>()).add(row[1]);
        }
        int maxId = FIRST_ID - 1;
        for (Listing listing : db.query("SELECT * FROM listings ORDER BY rowid", row -> toListing(row, users))) {
            listing.setPhotoPaths(photos.getOrDefault(listing.getId(), List.of()));
            listings.put(listing.getId(), listing);
            maxId = Math.max(maxId, numericId(listing.getId()));
        }
        idCounter.set(maxId + 1);
    }

    private static Listing toListing(ResultSet row, UserStore users) throws SQLException {
        String sellerName = row.getString("seller_username");
        if (!(users.find(sellerName) instanceof Seller seller)) {
            throw new IllegalStateException("Listing " + row.getString("id")
                    + " belongs to unknown seller " + sellerName);
        }
        String title = row.getString("title");
        String description = row.getString("description");
        Category category = Category.valueOf(row.getString("category"));
        Condition condition = Condition.valueOf(row.getString("condition"));
        Location location = Location.valueOf(row.getString("meetup_location"));
        Listing listing = switch (row.getString("type")) {
            case "SELL" -> new SaleListing(seller, title, description, category, condition, location,
                    new Money(Database.decimal(row, "price")));
            case "LEND" -> new LendListing(seller, title, description, category, condition, location,
                    Database.seconds(row, "lending_duration_seconds"));
            case "GIVEAWAY" -> new GiveawayListing(seller, title, description, category, condition, location);
            default -> throw new IllegalStateException("Unknown listing type " + row.getString("type"));
        };
        listing.setId(row.getString("id"));
        listing.setStatus(ListingStatus.valueOf(row.getString("status")));
        listing.setSubmittedDate(Database.dateTime(row, "submitted_date"));
        listing.setReviewedAt(Database.dateTime(row, "reviewed_at"));
        listing.setRejectionReason(row.getString("rejection_reason"));
        listing.setTakedownReason(row.getString("takedown_reason"));
        listing.setViewCount(row.getInt("view_count"));
        return listing;
    }

    private void savePhotos(Listing listing) {
        List<String> paths = listing.getPhotoPaths();
        for (int i = 0; i < paths.size(); i++) {
            db.update("INSERT INTO listing_photos (listing_id, position, path) VALUES (?, ?, ?)",
                    listing.getId(), i, paths.get(i));
        }
    }

    private static BigDecimal price(Listing listing) {
        return listing instanceof SaleListing sale ? sale.getPrice().amount() : null;
    }

    private static Duration lendingDuration(Listing listing) {
        return listing instanceof LendListing lend ? lend.getLendingDuration() : null;
    }

    /** The number in an {@code L123} id, or -1 for ids not made by this store. */
    private static int numericId(String id) {
        try {
            return id.startsWith("L") ? Integer.parseInt(id.substring(1)) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Returns the seeded listings so related stores can reference them. */
    List<Listing> seed(UserStore users) {
        List<Listing> seeded = new ArrayList<>();
        Seller charlie = (Seller) users.find("charlie");
        Seller diana = (Seller) users.find("diana");
        Random random = new Random();

        Listing textbook = new SaleListing(charlie, "Calculus Textbook (Stewart)",
                "Barely used, 8th edition. Perfect for MATH 1013.",
                Category.BOOKS_AND_NOTES, Condition.LIKE_NEW, Location.LIBRARY, Money.of("180"));
        textbook.setStatus(ListingStatus.LIVE);
        textbook.setSubmittedDate(LocalDateTime.now().minusDays(random.nextInt(10)));
        add(textbook);
        seeded.add(textbook);

        Listing charger = new SaleListing(charlie, "MacBook Pro Charger 61W",
                "Original Apple charger, works perfectly.",
                Category.ELECTRONICS, Condition.USED, Location.ACADEMIC_BUILDING, Money.of("250"));
        charger.setStatus(ListingStatus.LIVE);
        charger.setSubmittedDate(LocalDateTime.now().minusDays(random.nextInt(10)));
        add(charger);
        seeded.add(charger);

        Listing lamp = new GiveawayListing(diana, "IKEA Desk Lamp", "Good condition, LED bulb included.",
                Category.FURNITURE, Condition.USED, Location.UG_HALL_V);
        lamp.setStatus(ListingStatus.LIVE);
        lamp.setSubmittedDate(LocalDateTime.now().minusDays(random.nextInt(10)));
        add(lamp);
        seeded.add(lamp);

        Listing calculator = new LendListing(diana, "Graphing Calculator TI-84", "Can lend for one semester.",
                Category.ELECTRONICS, Condition.LIKE_NEW, Location.STUDENT_CENTRE, Duration.ofDays(90));
        calculator.setStatus(ListingStatus.LIVE);
        calculator.setSubmittedDate(LocalDateTime.now().minusDays(random.nextInt(10)));
        add(calculator);
        seeded.add(calculator);

        Listing fridge = new SaleListing(diana, "Mini Fridge", "Works well, cleaning required.",
                Category.FURNITURE, Condition.USED, Location.UG_HALL_II, Money.of("400"));
        fridge.setSubmittedDate(LocalDateTime.now().minusDays(random.nextInt(10)));
        add(fridge);
        seeded.add(fridge);

        return seeded;
    }
}
