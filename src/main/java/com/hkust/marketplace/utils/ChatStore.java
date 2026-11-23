package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory chat threads and their retained message history, one thread per
 * (Buyer, Listing) pair. All methods are synchronized because the brief requires
 * concurrent actions to be safe.
 *
 * <p>This store only stores and returns data. Participant checks and the offers-closed rule
 * live in {@link ChatService}, the public seam both portals call.
 */
public class ChatStore {

    /** thread key -> thread, in the order the threads were opened */
    private final Map<String, ChatThread> threads = new LinkedHashMap<>();
    /** thread key -> messages, in send order */
    private final Map<String, List<Message>> messages = new LinkedHashMap<>();

    /** Returns the single thread for this pair, creating it on first call. */
    public synchronized ChatThread getOrCreate(Buyer buyer, Listing listing) {
        String key = key(buyer, listing);
        return threads.computeIfAbsent(key, ignored -> new ChatThread(buyer, listing));
    }

    /** Appends a free-text message; the caller has already enforced access control. */
    public synchronized Message send(ChatThread thread, User sender, String content) {
        return append(thread, Message.text(content, sender));
    }

    /**
     * Appends a Formal Offer card for an already-created request, as one message. The caller
     * has already enforced access control and created the request through the deal lifecycle.
     */
    public synchronized Message sendFormalOffer(ChatThread thread, User sender,
                                                DealRequest dealRequest) {
        return append(thread, Message.formalOffer(sender, dealRequest));
    }

    /** The thread's messages in send-time order. History is never removed. */
    public synchronized List<Message> history(ChatThread thread) {
        List<Message> stored = messages.getOrDefault(key(thread), List.of());
        List<Message> ordered = new ArrayList<>(stored);
        // Stable sort: messages sent in the same instant keep their send order.
        ordered.sort(Comparator.comparing(Message::getSentTime));
        return List.copyOf(ordered);
    }

    /** Every thread the buyer participates in, oldest first. */
    public synchronized List<ChatThread> forBuyer(String username) {
        return threads.values().stream()
                .filter(thread -> sameUser(username, thread.getBuyer().getUsername()))
                .toList();
    }

    /** Every thread on the seller's listings, oldest first. */
    public synchronized List<ChatThread> forSeller(String username) {
        return threads.values().stream()
                .filter(thread -> sameUser(username, thread.getSeller().getUsername()))
                .toList();
    }

    /** Seeds one conversation on a live listing and one on a listing with a deal in progress. */
    void seed(Buyer buyer, Listing liveListing, Listing closedListing) {
        ChatThread live = getOrCreate(buyer, liveListing);
        send(live, buyer, "Hi! Is this still available?");
        send(live, liveListing.getSeller(), "Yes it is. When can you meet?");

        ChatThread closed = getOrCreate(buyer, closedListing);
        send(closed, buyer, "Thanks for the smooth deal!");
    }

    private Message append(ChatThread thread, Message message) {
        String key = key(thread);
        // Keep the canonical thread if an equal key was already opened.
        threads.putIfAbsent(key, thread);
        messages.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
        return message;
    }

    private static String key(ChatThread thread) {
        Objects.requireNonNull(thread, "thread");
        return key(thread.getBuyer(), thread.getListing());
    }

    private static String key(Buyer buyer, Listing listing) {
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(listing, "listing");
        String listingId = Objects.requireNonNull(listing.getId(), "listing id");
        return buyer.getUsername().toLowerCase() + "|" + listingId;
    }

    private static boolean sameUser(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }
}
