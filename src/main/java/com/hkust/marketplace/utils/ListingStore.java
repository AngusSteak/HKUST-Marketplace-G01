package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** In-memory listings with their fixed seed data. */
public class ListingStore {

    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(100);

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

    public void add(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + idCounter.getAndIncrement());
        }
        listings.put(listing.getId(), listing);
    }

    public void update(Listing listing) {
        listings.put(listing.getId(), listing);
    }

    public void remove(String id) {
        listings.remove(id);
    }

    /** Returns the seeded listings so related stores can reference them. */
    List<Listing> seed() {
        List<Listing> seeded = new ArrayList<>();
        seeded.add(addAndReturn(createListing("Calculus Textbook (Stewart)", "Barely used, 8th edition. Perfect for MATH 1013.",
                Category.BOOKS_AND_NOTES, ListingType.SELL, Condition.LIKE_NEW, 180.0, null, Location.LIBRARY, "charlie", "Charlie Lee")));
        seeded.add(addAndReturn(createListing("MacBook Pro Charger 61W", "Original Apple charger, works perfectly.",
                Category.ELECTRONICS, ListingType.SELL, Condition.USED, 250.0, null, Location.ACADEMIC_BUILDING, "charlie", "Charlie Lee")));
        seeded.add(addAndReturn(createListing("IKEA Desk Lamp", "Good condition, LED bulb included.",
                Category.FURNITURE, ListingType.GIVEAWAY, Condition.USED, null, null, Location.UG_HALL_V, "diana", "Diana Ng")));
        seeded.add(addAndReturn(createListing("Graphing Calculator TI-84", "Can lend for one semester.",
                Category.ELECTRONICS, ListingType.LEND, Condition.LIKE_NEW, null, 90, Location.STUDENT_CENTRE, "diana", "Diana Ng")));
        Listing pending = createListing("Mini Fridge", "Works well, cleaning required.",
                Category.FURNITURE, ListingType.SELL, Condition.USED, 400.0, null, Location.UG_HALL_II, "diana", "Diana Ng");
        pending.setStatus(ListingStatus.PENDING_REVIEW);
        seeded.add(addAndReturn(pending));
        return seeded;
    }

    private Listing addAndReturn(Listing listing) {
        add(listing);
        return listing;
    }

    private Listing createListing(String title, String desc, Category cat, ListingType type,
                                  Condition condition, Double price, Integer days,
                                  Location meetup, String sellerUser, String sellerName) {
        Listing l = new Listing();
        l.setTitle(title);
        l.setDescription(desc);
        l.setCategory(cat);
        l.setType(type);
        l.setCondition(condition);
        l.setPrice(price);
        l.setLendingDays(days);
        l.setMeetupLocation(meetup);
        l.setSellerUsername(sellerUser);
        l.setSellerFullName(sellerName);
        l.setPostedAt(LocalDateTime.now().minusDays(new Random().nextInt(10)));
        l.setStatus(ListingStatus.LIVE);
        return l;
    }
}
