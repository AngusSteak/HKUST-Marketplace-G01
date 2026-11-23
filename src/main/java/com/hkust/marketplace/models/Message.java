package com.hkust.marketplace.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One entry in a {@link ChatThread} (ADR-0005). A single final class with a private
 * constructor and named factories, so a message can never be built in an invalid shape.
 *
 * <p>There are exactly two shapes: {@link #text(String, User)} carries user-authored text and
 * no request link, while {@link #formalOffer(User, DealRequest)} carries a linked request and
 * a generated summary instead of user text. Because the offer factory has no content
 * parameter, a mixed text-and-offer message is unconstructible.
 */
public final class Message {

    private final String content;
    private final LocalDateTime sentTime;
    private final User sender;
    /** The request behind a Formal Offer; {@code null} for a plain text message. */
    private final DealRequest dealRequest;

    private Message(String content, LocalDateTime sentTime, User sender, DealRequest dealRequest) {
        this.content = content;
        this.sentTime = sentTime;
        this.sender = sender;
        this.dealRequest = dealRequest;
    }

    /** Creates a free-text message. Empty or blank content is rejected. */
    public static Message text(String content, User sender) {
        Objects.requireNonNull(sender, "sender");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
        return new Message(content, LocalDateTime.now(), sender, null);
    }

    /**
     * Creates a Formal Offer from {@code request}. There is deliberately no content parameter:
     * the stored content is a generated summary of the request's terms (used for keyword search
     * and card labelling), never user-authored text. The summary omits the request status,
     * because status changes after the offer is made.
     */
    public static Message formalOffer(User sender, DealRequest request) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(request, "dealRequest");
        return new Message(summarize(request), LocalDateTime.now(), sender, request);
    }

    /**
     * Rebuilds a saved message exactly as stored: its original time and, for a Formal Offer,
     * the stored summary and the restored request. Only for loading the database.
     */
    public static Message restore(String content, LocalDateTime sentTime, User sender,
                                  DealRequest dealRequest) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(sentTime, "sentTime");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
        return new Message(content, sentTime, sender, dealRequest);
    }

    public String getContent() { return content; }

    public LocalDateTime getSentTime() { return sentTime; }

    public User getSender() { return sender; }

    /** Convenience mirror of the bean-style username accessors used by the other models. */
    public String getSenderUsername() { return sender.getUsername(); }

    /** The request behind a Formal Offer, or {@code null} for a plain text message. */
    public DealRequest getDealRequest() { return dealRequest; }

    /** True only when this message carries a Formal Offer; derived from the request link. */
    public boolean isFormalOffer() { return dealRequest != null; }

    /** Generated, status-free summary of the request's terms plus the Listing title. */
    private static String summarize(DealRequest request) {
        String title = request.getListing().getTitle();
        if (request instanceof PriceOffer offer) {
            return "Offer of " + new Money(offer.getOfferedPrice()).getDisplay() + " for " + title;
        }
        if (request instanceof BorrowRequest borrow) {
            Duration period = Duration.between(borrow.getStartDate(), borrow.getEndDate());
            return "Borrow request for " + describe(period) + " for " + title;
        }
        return "Claim for " + title;
    }

    /**
     * Human-readable duration for the summary. Kept local because a model must not depend on
     * the presentation helpers in {@code utils}; mirrors {@code DurationText}'s two-unit style.
     */
    private static String describe(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long[] amounts = {
                totalSeconds / 86_400,
                (totalSeconds % 86_400) / 3_600,
                (totalSeconds % 3_600) / 60,
                totalSeconds % 60
        };
        String[] names = {"day", "hour", "minute", "second"};
        List<String> parts = new ArrayList<>(2);
        for (int i = 0; i < amounts.length && parts.size() < 2; i++) {
            if (amounts[i] > 0) {
                parts.add(amounts[i] + " " + names[i] + (amounts[i] == 1 ? "" : "s"));
            }
        }
        return parts.isEmpty() ? "0 seconds" : String.join(" ", parts);
    }
}
