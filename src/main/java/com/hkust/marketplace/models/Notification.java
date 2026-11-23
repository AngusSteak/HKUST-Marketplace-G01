package com.hkust.marketplace.models;

import java.time.LocalDateTime;

public class Notification {
    private String id;
    private String username; // recipient
    private String title;
    private String message;
    private NotificationCategory category;
    private NotificationRef relatedRef; // where OPEN navigates; null for announcements
    private LocalDateTime timestamp;
    private boolean read;

    public Notification() {
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    public Notification(String username, String title, String message, NotificationCategory category) {
        this(username, title, message, category, null);
    }

    public Notification(String username, String title, String message,
                        NotificationCategory category, NotificationRef relatedRef) {
        this();
        this.username = username;
        this.title = title;
        this.message = message;
        this.category = category;
        this.relatedRef = relatedRef;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationCategory getCategory() { return category; }
    public void setCategory(NotificationCategory category) { this.category = category; }

    public NotificationRef getRelatedRef() { return relatedRef; }
    public void setRelatedRef(NotificationRef relatedRef) { this.relatedRef = relatedRef; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
