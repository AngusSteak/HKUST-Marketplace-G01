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

import java.time.Duration;
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

    /**
     * Returns the id the next newly created listing would receive, WITHOUT incrementing
     * the counter. The submit flow pre-sets this id on a listing before saving so photos
     * can be keyed to the directory that listing will actually get; {@link #add(Listing)}
     * preserves a pre-set id because of its {@code getId() == null} guard.
     *
     * <p>Assumes the single-threaded demo usage of this store.
     */
    public String peekNextId() {
        return "L" + idCounter.get();
    }

    public void add(Listing listing) {
        // Always advance the counter, even for a pre-set id: peekNextId() does not
        // increment, so the submit flow's reserved id must still consume its number.
        String nextId = "L" + idCounter.getAndIncrement();
        if (listing.getId() == null) {
            listing.setId(nextId);
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
