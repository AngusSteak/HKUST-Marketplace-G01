package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** In-memory notification board, one list shared by all recipients. */
public class NotificationStore {

    private final List<Notification> notifications = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public List<Notification> forUser(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void add(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + idCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    void seed() {
        add(new Notification("alice", "Offer Accepted",
                "Charlie accepted your offer of HK$160 for Calculus Textbook.", "Offer"));
        add(new Notification("charlie", "New Offer Received",
                "Alice offered HK$160 for your Calculus Textbook.", "Offer"));
        add(new Notification("diana", "Listing Pending",
                "Your Mini Fridge listing is awaiting admin review.", "System"));
        add(new Notification("admin", "New Listing Submitted",
                "Diana submitted 'Mini Fridge' for review.", "System"));
    }
}
