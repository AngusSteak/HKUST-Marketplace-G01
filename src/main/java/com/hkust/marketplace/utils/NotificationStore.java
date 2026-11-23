package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

    /**
     * The single way the application creates notifications: every one has an SRS category
     * and, where applicable, a reference to the object an OPEN should navigate to.
     */
    public Notification notify(String recipient, NotificationCategory category,
                               String content, NotificationRef relatedRef) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(category, "category");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Notification content must not be blank");
        }
        Notification n = new Notification(recipient, category.getLabel(), content, category, relatedRef);
        add(n);
        return n;
    }

    public void add(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + idCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    void seed() {
        notify("alice", NotificationCategory.OFFER_UPDATE,
                "Charlie accepted your offer of HK$160 for Calculus Textbook.",
                new NotificationRef(NotificationRef.Target.DEAL, "D1"));
        // Sellers get New Request for incoming offers; Offer Update is the buyer-side category (SRS UC-2.7).
        notify("charlie", NotificationCategory.NEW_REQUEST,
                "Alice offered HK$160 for your Calculus Textbook.",
                new NotificationRef(NotificationRef.Target.REQUEST, "Q1"));
        // No seller notice for a pending submission: New Submission is admin-only and UC-2.3
        // only confirms on screen to the seller, so only the admin is notified.
        notify("admin", NotificationCategory.NEW_SUBMISSION,
                "Diana submitted 'Mini Fridge' for review.",
                new NotificationRef(NotificationRef.Target.LISTING, "L104"));
    }
}
