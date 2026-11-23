package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.CounterOffer;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chat threads and their retained message history, one thread per (Buyer, Listing) pair.
 * Threads and messages are saved to the {@code chat_threads} and {@code messages} tables as
 * they are created; {@link #load} rebuilds them at startup. All methods are synchronized
 * because the brief requires concurrent actions to be safe.
 *
 * <p>This store only stores and returns data. Participant checks and the offers-closed rule
 * live in {@link ChatService}, the public seam both portals call.
 */
public class ChatStore {

    /** thread key -> thread, in the order the threads were opened */
    private final Map<String, ChatThread> threads = new LinkedHashMap<>();
    /** thread key -> messages, in send order */
    private final Map<String, List<Message>> messages = new LinkedHashMap<>();
    private final Database db;

    ChatStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    /** Returns the single thread for this pair, creating it on first call. */
    public synchronized ChatThread getOrCreate(Buyer buyer, Listing listing) {
        String key = key(buyer, listing);
        ChatThread existing = threads.get(key);
        if (existing != null) {
            return existing;
        }
        ChatThread thread = new ChatThread(buyer, listing);
        saveThread(thread);
        threads.put(key, thread);
        return thread;
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

    /** Appends a Counter Offer card for an already-created seller counter, as its own message. */
    public synchronized Message sendCounterOffer(ChatThread thread, User sender,
                                                 CounterOffer counter) {
        return append(thread, Message.counterOffer(sender, counter));
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
        boolean newThread = !threads.containsKey(key);
        db.inTransaction(() -> {
            if (newThread) {
                saveThread(thread);
            }
            DealRequest request = message.getDealRequest();
            db.update("INSERT INTO messages (buyer_username, listing_id, sender_username, content,"
                            + " sent_time, deal_request_id) VALUES (?, ?, ?, ?, ?, ?)",
                    thread.getBuyer().getUsername().toLowerCase(), thread.getListing().getId(),
                    message.getSenderUsername().toLowerCase(), message.getContent(),
                    message.getSentTime(), request == null ? null : request.getId());
        });
        // Only after the save succeeded, so memory never holds a thread or message the database lacks.
        // Keep the canonical thread if an equal key was already opened.
        threads.putIfAbsent(key, thread);
        messages.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
        return message;
    }

    private void saveThread(ChatThread thread) {
        db.update("INSERT OR IGNORE INTO chat_threads (buyer_username, listing_id) VALUES (?, ?)",
                thread.getBuyer().getUsername().toLowerCase(), thread.getListing().getId());
    }

    /**
     * Rebuilds threads in the order they were opened and their messages in send order, on the
     * already loaded users, listings and deal requests (a Formal Offer links its request).
     */
    synchronized void load(UserStore users, ListingStore listings, DealStore deals) {
        db.query("SELECT buyer_username, listing_id FROM chat_threads ORDER BY rowid", row -> {
            Buyer buyer = (Buyer) require(users.find(row.getString("buyer_username")), "buyer", row.getString(1));
            Listing listing = require(listings.find(row.getString("listing_id")), "listing", row.getString(2));
            ChatThread thread = new ChatThread(buyer, listing);
            threads.put(key(thread), thread);
            return thread;
        });
        db.query("SELECT * FROM messages ORDER BY id", row -> {
            String key = row.getString("buyer_username") + "|" + row.getString("listing_id");
            String requestId = row.getString("deal_request_id");
            Message message = Message.restore(row.getString("content"),
                    Database.dateTime(row, "sent_time"),
                    require(users.find(row.getString("sender_username")), "sender", row.getString("sender_username")),
                    requestId == null ? null : require(deals.findRequest(requestId), "request", requestId));
            messages.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
            return message;
        });
    }

    private static <T> T require(T found, String what, String id) {
        if (found == null) {
            throw new IllegalStateException("Saved chat refers to unknown " + what + " " + id);
        }
        return found;
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
