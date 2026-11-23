package com.hkust.marketplace.models;

import java.util.Arrays;

/** The 14 notification categories defined in SRS Appendix A. */
public enum NotificationCategory {
    OFFER_UPDATE("Offer Update"),
    NEW_REQUEST("New Request"),
    CHAT_MESSAGE("Chat Message"),
    BORROW_REMINDER("Borrow Reminder"),
    LISTING_APPROVED("Listing Approved"),
    LISTING_REJECTED("Listing Rejected"),
    LISTING_REMOVED("Listing Removed"),
    REVIEW_RECEIVED("Review Received"),
    REVIEW_REPLY("Review Reply"),
    DEAL_COMPLETED("Deal Completed"),
    NEW_SUBMISSION("New Submission"),
    REPORTED_LISTING("Reported Listing"),
    ACCOUNT_CHANGE("Account Change"),
    ANNOUNCEMENT("Announcement");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Resolves the human-readable form, e.g. when migrating old free-text categories. */
    public static NotificationCategory fromLabel(String label) {
        return Arrays.stream(values())
                .filter(c -> c.label.equalsIgnoreCase(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification category: " + label));
    }
}
