package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.persistence.Database;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The application's data, split into per-area stores and backed by a local SQLite database
 * ({@value #DATABASE_FILE}).
 *
 * <p>Users, listings (#55), deals, favourites, follows (#56) and chats (#57) are saved to the
 * database; the other stores are still in-memory until #58 persists them. Until then the database is recreated and re-seeded on
 * every launch (see {@link #RESET_PROPERTY}), so all stores stay consistent with each other.
 */
public class DummyDataService {

    /** Where the app keeps its database, relative to the working directory. */
    public static final String DATABASE_FILE = "data/marketplace.db";

    /**
     * System property: {@code true} recreates the database on every launch, {@code false} keeps
     * saved data across restarts. Defaults to {@code true} until every store is persisted.
     * TODO(#59): default to false once #58 is merged.
     */
    public static final String RESET_PROPERTY = "marketplace.resetDb";

    private static DummyDataService instance;

    private final Database db;
    private final UserStore users;
    private final ListingStore listings;
    private final DealStore deals;
    private final NotificationStore notifications = new NotificationStore();
    private final ReviewStore reviews = new ReviewStore();
    private final FavouriteStore favourites;
    private final AuditStore audit = new AuditStore();
    private final ReportStore reports = new ReportStore();
    private final ChatStore chats;

    /**
     * Creates an empty store on a private in-memory database, with no demo data. Visible for
     * tests so each test works on an isolated instance; application code must use
     * {@link #getInstance()}.
     */
    DummyDataService() {
        this(Database.inMemory());
    }

    /** Creates the stores on {@code db} without loading or seeding anything. */
    DummyDataService(Database db) {
        this.db = Objects.requireNonNull(db, "db");
        this.users = new UserStore(db);
        this.listings = new ListingStore(db);
        this.deals = new DealStore(db, listings);
        this.favourites = new FavouriteStore(db);
        this.chats = new ChatStore(db);
    }

    /** The shared instance, opened on first use from {@value #DATABASE_FILE}. */
    public static synchronized DummyDataService getInstance() {
        if (instance == null) {
            boolean reset = Boolean.parseBoolean(System.getProperty(RESET_PROPERTY, "true"));
            instance = open(Database.openFile(Path.of(DATABASE_FILE), reset));
        }
        return instance;
    }

    /**
     * Opens the stores on {@code db}: a fresh (empty) database is seeded with the demo data,
     * otherwise the saved data is loaded (users first, since everything else refers to them).
     */
    static DummyDataService open(Database db) {
        DummyDataService data = new DummyDataService(db);
        if (db.isEmpty("users")) {
            data.seedData();
        } else {
            data.users.load();
            data.listings.load(data.users);
            data.deals.load(data.users, data.listings);
            data.favourites.load();
            data.chats.load(data.users, data.listings, data.deals);
        }
        return data;
    }

    /** Closes the shared instance's database, e.g. when the application exits. */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.db.close();
            instance = null;
        }
    }

    /** The database behind the stores, for stores that persist themselves (#56, #57, #58). */
    public Database database() { return db; }

    public UserStore users() { return users; }
    public ListingStore listings() { return listings; }
    public DealStore deals() { return deals; }
    public NotificationStore notifications() { return notifications; }
    public ReviewStore reviews() { return reviews; }
    public FavouriteStore favourites() { return favourites; }
    public AuditStore audit() { return audit; }
    public ReportStore reports() { return reports; }
    public ChatStore chats() { return chats; }

    /**
     * Populates this store with the demo data. Visible for tests so they can seed an
     * isolated instance instead of sharing the singleton.
     */
    void seedData() {
        users.seed();
        List<Listing> seededListings = listings.seed(users);
        // Requests and deals on L100 (textbook), L102 (lamp) and L103 (calculator); see DealStore.seed
        deals.seed((Buyer) users.find("alice"), (Buyer) users.find("bob"),
                seededListings.get(0), seededListings.get(2), seededListings.get(3));
        notifications.seed();
        reviews.seed();
        // alice favourites the MacBook charger (L101) and follows its seller charlie
        favourites.seed((Buyer) users.find("alice"), seededListings.get(1), (Seller) users.find("charlie"));
        // a chat on the live charger, and one on alice's reserved textbook (messages stay open, offers closed)
        chats.seed((Buyer) users.find("alice"), seededListings.get(1), seededListings.get(0));
    }
}
