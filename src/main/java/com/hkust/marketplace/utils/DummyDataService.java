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
 * <p>Every store is saved to the database: users and listings (#55); deals, favourites and
 * follows (#56); chats (#57); notifications, reviews, reports and the audit log (#58). The
 * database is kept across restarts (#59); it is seeded with the demo data only when empty.
 */
public class DummyDataService {

    /** Where the app keeps its database, relative to the working directory. */
    public static final String DATABASE_FILE = "data/marketplace.db";

    /**
     * System property: {@code true} deletes the database at launch and starts again from the demo
     * data; {@code false} (the default) keeps saved data across restarts.
     */
    public static final String RESET_PROPERTY = "marketplace.resetDb";

    private static DummyDataService instance;

    private final Database db;
    private final UserStore users;
    private final ListingStore listings;
    private final DealStore deals;
    private final NotificationStore notifications;
    private final ReviewStore reviews;
    private final FavouriteStore favourites;
    private final AuditStore audit;
    private final ReportStore reports;
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
        this.notifications = new NotificationStore(db);
        this.reviews = new ReviewStore(db);
        this.reports = new ReportStore(db);
        this.audit = new AuditStore(db);
    }

    /** The shared instance, opened on first use from {@value #DATABASE_FILE}. */
    public static synchronized DummyDataService getInstance() {
        if (instance == null) {
            boolean reset = Boolean.parseBoolean(System.getProperty(RESET_PROPERTY, "false"));
            Database db = Database.openFile(Path.of(DATABASE_FILE), reset);
            try {
                instance = open(db);
            } catch (RuntimeException e) {
                db.close();   // release the file and its lock if loading or seeding fails
                throw e;
            }
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
            data.notifications.load();
            data.reviews.load();
            data.reports.load(data.users, data.listings);
            data.audit.load();
        }
        return data;
    }

    /**
     * Tests only: makes {@code data} the shared instance, so screen tests run on an in-memory
     * database instead of {@value #DATABASE_FILE}; {@code null} clears it again.
     */
    static synchronized void replaceInstanceForTests(DummyDataService data) {
        instance = data;
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

    /** The user store. */
    public UserStore users() { return users; }
    /** The listing store. */
    public ListingStore listings() { return listings; }
    /** The request and deal store. */
    public DealStore deals() { return deals; }
    /** The notification store. */
    public NotificationStore notifications() { return notifications; }
    /** The review store. */
    public ReviewStore reviews() { return reviews; }
    /** The favourites/follows store. */
    public FavouriteStore favourites() { return favourites; }
    /** The admin audit log. */
    public AuditStore audit() { return audit; }
    /** The listing report store. */
    public ReportStore reports() { return reports; }
    /** The chat store. */
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
