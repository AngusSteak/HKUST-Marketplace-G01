package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.persistence.Database;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Notification board, one list shared by all recipients. Every notification is saved to the
 * {@code notifications} table when it is added, and its read flag by {@link #markRead};
 * {@link #load()} rebuilds them at startup.
 */
public class NotificationStore {

    private final List<Notification> notifications = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Database db;

    NotificationStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public synchronized List<Notification> forUser(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /** The user's notifications of one category, newest first; {@code null} means every category. */
    public synchronized List<Notification> forUser(String username, NotificationCategory category) {
        return forUser(username).stream()
                .filter(n -> category == null || n.getCategory() == category)
                .collect(Collectors.toList());
    }

    /** How many of the user's notifications are still unread. */
    public synchronized long unreadCount(String username) {
        return forUser(username).stream().filter(n -> !n.isRead()).count();
    }

    /**
     * The single way the application creates notifications: every one has an SRS category
     * and, where applicable, a reference to the object an OPEN should navigate to.
     */
    public synchronized Notification notify(String recipient, NotificationCategory category,
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

    public synchronized void add(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + idCounter.getAndIncrement());
        }
        NotificationRef ref = n.getRelatedRef();
        db.update("INSERT INTO notifications (id, recipient_username, title, message, category,"
                        + " related_target, related_id, timestamp, read) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                n.getId(), n.getUsername().toLowerCase(), n.getTitle(), n.getMessage(), n.getCategory(),
                ref == null ? null : ref.getTarget(), ref == null ? null : ref.getId(),
                n.getTimestamp(), n.isRead());
        notifications.add(n);
    }

    /** Marks the notification read (or unread) and saves the flag. */
    public synchronized void markRead(Notification n, boolean read) {
        Objects.requireNonNull(n, "notification");
        int rows = db.update("UPDATE notifications SET read = ? WHERE id = ?", read, n.getId());
        if (rows == 0) {
            throw new IllegalArgumentException("Unknown notification: " + n.getId());
        }
        n.setRead(read);
    }

    /** Rebuilds the saved notifications, oldest first, and resumes the {@code N} id counter. */
    synchronized void load() {
        db.query("SELECT * FROM notifications ORDER BY CAST(substr(id, 2) AS INTEGER)", row -> {
            String target = row.getString("related_target");
            Notification n = new Notification(row.getString("recipient_username"), row.getString("title"),
                    row.getString("message"), NotificationCategory.valueOf(row.getString("category")),
                    target == null ? null : new NotificationRef(NotificationRef.Target.valueOf(target),
                            row.getString("related_id")));
            n.setId(row.getString("id"));
            n.setTimestamp(Database.dateTime(row, "timestamp"));
            n.setRead(Database.bool(row, "read"));
            notifications.add(n);
            idCounter.set(Math.max(idCounter.get(), Integer.parseInt(n.getId().substring(1)) + 1));
            return n;
        });
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
