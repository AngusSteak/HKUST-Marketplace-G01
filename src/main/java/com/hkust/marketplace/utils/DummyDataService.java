package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Seller;

import java.util.List;

/**
 * In-memory data store for the frontend demo, split into per-area stores.
 * All data is lost when the application closes.
 * TODO: Replace these stores with real repositories / API clients.
 */
public class DummyDataService {

    private static final DummyDataService INSTANCE = createSeededInstance();

    private final UserStore users = new UserStore();
    private final ListingStore listings = new ListingStore();
    private final DealStore deals = new DealStore();
    private final NotificationStore notifications = new NotificationStore();
    private final ReviewStore reviews = new ReviewStore();
    private final FavouriteStore favourites = new FavouriteStore();
    private final AuditStore audit = new AuditStore();
    private final ReportStore reports = new ReportStore();

    /**
     * Creates an empty store with no demo data. Visible for tests so each test can
     * work on an isolated instance; application code must use {@link #getInstance()}.
     */
    DummyDataService() {
    }

    private static DummyDataService createSeededInstance() {
        DummyDataService store = new DummyDataService();
        store.seedData();
        return store;
    }

    public static DummyDataService getInstance() {
        return INSTANCE;
    }

    public UserStore users() { return users; }
    public ListingStore listings() { return listings; }
    public DealStore deals() { return deals; }
    public NotificationStore notifications() { return notifications; }
    public ReviewStore reviews() { return reviews; }
    public FavouriteStore favourites() { return favourites; }
    public AuditStore audit() { return audit; }
    public ReportStore reports() { return reports; }

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
    }
}
