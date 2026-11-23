package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.CounterOffer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The public seam for chat. Both the buyer and seller portals go through this service, so
 * there is exactly one place that decides who may send and whether a thread accepts offers.
 * Access control lives here, not in the UI and not in {@link ChatStore}.
 */
public final class ChatService {

    /**
     * Shown on a thread whose Listing is no longer live, and used as the refusal message for a
     * blocked Formal Offer. Plain messages are never blocked: buyer and seller keep talking
     * through a reserved or lent deal and after it completes (SRS UC-1.5 only closes offers).
     */
    public static final String OFFERS_CLOSED_BANNER =
            "This listing is no longer live \u2014 new offers are disabled.";

    private final DummyDataService data;

    public ChatService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Returns the one thread for this (Buyer, Listing) pair, creating it on first use. */
    public ChatThread openThread(Buyer buyer, Listing listing) {
        return data.chats().getOrCreate(buyer, listing);
    }

    /**
     * Sends a free-text message. Only the thread's Buyer or the Listing's Seller may send.
     * Messages stay open whatever the Listing's status, so the two can arrange a meet-up or
     * return and follow up after the deal. Empty content is rejected.
     *
     * @throws SecurityException if {@code sender} is not a participant of the thread
     * @throws IllegalArgumentException if the content is blank
     */
    public Message sendText(User sender, ChatThread thread, String content) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(thread, "thread");
        requireParticipant(sender, thread);
        return data.chats().send(thread, sender, content);
    }

    /**
     * Submits a price offer on the thread's Sale Listing and posts it as one Formal Offer card.
     * Only the thread's Buyer may offer, and only while the Listing is live.
     *
     * @throws SecurityException if {@code sender} is not the thread's Buyer
     * @throws IllegalStateException if the Listing is no longer live
     * @throws IllegalArgumentException if the Listing is not for sale or the price is invalid
     */
    public Message submitFormalOffer(User sender, ChatThread thread, BigDecimal price) {
        Buyer buyer = requireThreadBuyer(sender, thread);
        requireLive(thread.getListing());
        requireType(thread, ListingType.SELL);
        DealRequest request = data.deals().lifecycle().submitOffer(buyer, thread.getListing(), price);
        return data.chats().sendFormalOffer(thread, buyer, request);
    }

    /**
     * Submits a borrow request on the thread's Lend Listing and posts it as one Formal Offer
     * card. Only the thread's Buyer may request, and only while the Listing is live.
     *
     * @throws SecurityException if {@code sender} is not the thread's Buyer
     * @throws IllegalStateException if the Listing is no longer live
     * @throws IllegalArgumentException if the Listing is not for lending or the period is invalid
     */
    public Message submitFormalOffer(User sender, ChatThread thread,
                                     LocalDateTime start, LocalDateTime end) {
        Buyer buyer = requireThreadBuyer(sender, thread);
        requireLive(thread.getListing());
        requireType(thread, ListingType.LEND);
        DealRequest request = data.deals().lifecycle()
                .submitBorrow(buyer, thread.getListing(), start, end);
        return data.chats().sendFormalOffer(thread, buyer, request);
    }

    /**
     * Submits a claim on the thread's Giveaway Listing and posts it as one Formal Offer card.
     * Only the thread's Buyer may claim, and only while the Listing is live.
     *
     * @throws SecurityException if {@code sender} is not the thread's Buyer
     * @throws IllegalStateException if the Listing is no longer live
     * @throws IllegalArgumentException if the Listing is not a giveaway
     */
    public Message submitFormalOffer(User sender, ChatThread thread) {
        Buyer buyer = requireThreadBuyer(sender, thread);
        requireLive(thread.getListing());
        requireType(thread, ListingType.GIVEAWAY);
        DealRequest request = data.deals().lifecycle().submitClaim(buyer, thread.getListing());
        return data.chats().sendFormalOffer(thread, buyer, request);
    }

    /**
     * Accepts {@code request} on behalf of the Listing's Seller. This is a thin wrapper:
     * every transition, deal-creation and Listing-status change stays in
     * {@link com.hkust.marketplace.models.DealLifecycle#accept}. The card is re-rendered
     * from the request's live status, never from a local flag.
     *
     * @throws SecurityException if {@code seller} is not the Seller of the request's Listing
     * @throws IllegalStateException if the request is not pending
     */
    public Deal acceptOffer(User seller, DealRequest request) {
        requireListingSeller(seller, request);
        return data.deals().lifecycle().accept(request);
    }

    /**
     * Declines {@code request} on behalf of the Listing's Seller. This is a thin wrapper:
     * the status change and any Listing relist stay in
     * {@link com.hkust.marketplace.models.DealLifecycle#decline}. The request and its card
     * remain in the retained history.
     *
     * @throws SecurityException if {@code seller} is not the Seller of the request's Listing
     * @throws IllegalStateException if the request is not pending
     */
    public DealRequest declineOffer(User seller, DealRequest request) {
        requireListingSeller(seller, request);
        return data.deals().lifecycle().decline(request);
    }

    /**
     * Counters a pending price offer with a new price on behalf of the Listing's Seller, and
     * posts the counter as its own card in the thread. The buyer's offer card is left untouched,
     * and the new counter can no longer be edited: send a new one only after cancelling this one.
     *
     * @throws SecurityException if {@code seller} is not the Seller of the request's Listing
     * @throws IllegalStateException if the Listing is not live, the request is not pending, or
     *         an earlier counter is still unanswered
     * @throws IllegalArgumentException if the request is not a price offer or the price is invalid
     */
    public Message counterOffer(User seller, DealRequest request, BigDecimal price) {
        requireListingSeller(seller, request);
        if (!(request instanceof PriceOffer offer)) {
            throw new IllegalArgumentException("Only a price offer can be countered with a price");
        }
        requireLive(request.getListing());
        CounterOffer counter = data.deals().lifecycle().counterOffer(offer, price);
        return data.chats().sendCounterOffer(threadFor(request), seller, counter);
    }

    /**
     * Counters a pending borrow request with another lending period on behalf of the Listing's
     * Seller, and posts it as its own card. Like a price counter it is immutable once sent.
     *
     * @throws SecurityException if {@code seller} is not the Seller of the request's Listing
     * @throws IllegalStateException if the Listing is not live, the request is not pending, or
     *         an earlier counter is still unanswered
     * @throws IllegalArgumentException if the request is not a borrow request or the period is invalid
     */
    public Message counterOffer(User seller, DealRequest request,
                                LocalDateTime start, LocalDateTime end) {
        requireListingSeller(seller, request);
        if (!(request instanceof BorrowRequest borrow)) {
            throw new IllegalArgumentException(
                    "Only a borrow request can be countered with a lending period");
        }
        requireLive(request.getListing());
        CounterOffer counter = data.deals().lifecycle().counterBorrow(borrow, start, end);
        return data.chats().sendCounterOffer(threadFor(request), seller, counter);
    }

    /**
     * Withdraws an unanswered counter on behalf of the Listing's Seller. The buyer's request
     * returns to pending, so the seller may accept it, decline it, or send a fresh counter.
     *
     * @throws SecurityException if {@code seller} is not the Listing's Seller
     * @throws IllegalStateException if the counter is not still offered
     */
    public CounterOffer cancelCounter(User seller, CounterOffer counter) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(counter, "counter");
        if (!seller.getUsername().equalsIgnoreCase(counter.getSellerUsername())) {
            throw new SecurityException("Only the listing's seller may cancel a counter");
        }
        return data.deals().lifecycle().cancelCounter(counter);
    }

    /**
     * Accepts the seller's counter on behalf of the request's Buyer, creating the Deal on the
     * countered terms through the lifecycle.
     *
     * @throws SecurityException if {@code buyer} is not the request's Buyer
     * @throws IllegalStateException if the counter is not offered or the Listing is unavailable
     */
    public Deal acceptCounter(User buyer, CounterOffer counter) {
        requireCounterBuyer(buyer, counter);
        return data.deals().lifecycle().acceptCounter(counter);
    }

    /**
     * Declines the seller's counter on behalf of the request's Buyer. The counter card stays in
     * the retained history with a declined status.
     *
     * @throws SecurityException if {@code buyer} is not the request's Buyer
     * @throws IllegalStateException if the counter is not offered
     */
    public CounterOffer declineCounter(User buyer, CounterOffer counter) {
        requireCounterBuyer(buyer, counter);
        return data.deals().lifecycle().declineCounter(counter);
    }

    private ChatThread threadFor(DealRequest request) {
        return data.chats().getOrCreate(request.getBuyer(), request.getListing());
    }

    /** All messages in the thread, oldest (send-time) first. History is never removed. */
    public List<Message> history(ChatThread thread) {
        return data.chats().history(Objects.requireNonNull(thread, "thread"));
    }

    /** True while the Listing is live, so the buyer may still post a Formal Offer (UC-1.5 A2). */
    public boolean acceptsOffers(ChatThread thread) {
        Objects.requireNonNull(thread, "thread");
        return thread.getListing().getStatus() == ListingStatus.LIVE;
    }

    /** Every conversation the buyer participates in. */
    public List<ChatThread> threadsForBuyer(Buyer buyer) {
        Objects.requireNonNull(buyer, "buyer");
        return data.chats().forBuyer(buyer.getUsername());
    }

    /** Every conversation on the seller's listings. */
    public List<ChatThread> threadsForSeller(Seller seller) {
        Objects.requireNonNull(seller, "seller");
        return data.chats().forSeller(seller.getUsername());
    }

    /**
     * Searches the messages of every thread the user participates in. A Buyer sees only
     * their own threads and a Seller only threads on their Listings, so search can never
     * leak another user's private conversation.
     *
     * <p>Matching is a case-insensitive substring test over {@link Message#getContent()}.
     * A Formal Offer's content is its generated summary, so offers are found by their
     * price, lending period or Listing title like any other text. {@code filter} selects
     * the kinds: {@code TEXT_ONLY} drops offers, {@code OFFERS_ONLY} keeps only offers and
     * {@code ALL} keeps both.
     *
     * <p>A blank keyword returns everything only under {@code OFFERS_ONLY}, which doubles as
     * an "all offers" listing. Under {@code ALL} and {@code TEXT_ONLY} a blank keyword returns
     * nothing, so an accidental empty search cannot dump a user's whole history. Results are
     * ordered newest-first.
     *
     * @throws NullPointerException if {@code user} or {@code filter} is {@code null}
     */
    public List<Message> search(User user, String keyword, MessageSearchFilter filter) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(filter, "filter");
        boolean blank = keyword == null || keyword.isBlank();
        if (blank && filter != MessageSearchFilter.OFFERS_ONLY) {
            return List.of();
        }
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Message> matches = new ArrayList<>();
        for (ChatThread thread : participatingThreads(user)) {
            for (Message message : data.chats().history(thread)) {
                if (!matchesKind(message, filter)) {
                    continue;
                }
                if (!needle.isEmpty()
                        && !message.getContent().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                matches.add(message);
            }
        }
        matches.sort(Comparator.comparing(Message::getSentTime).reversed());
        return List.copyOf(matches);
    }

    private List<ChatThread> participatingThreads(User user) {
        if (user instanceof Buyer buyer) {
            return threadsForBuyer(buyer);
        }
        if (user instanceof Seller seller) {
            return threadsForSeller(seller);
        }
        return List.of();
    }

    private static boolean matchesKind(Message message, MessageSearchFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case TEXT_ONLY -> !message.isOfferCard();
            case OFFERS_ONLY -> message.isOfferCard();
        };
    }

    private static void requireListingSeller(User actor, DealRequest request) {
        Objects.requireNonNull(actor, "seller");
        Objects.requireNonNull(request, "request");
        if (!actor.getUsername().equalsIgnoreCase(request.getSellerUsername())) {
            throw new SecurityException(
                    "Only the listing's seller may accept, decline or counter an offer");
        }
    }

    private static void requireCounterBuyer(User actor, CounterOffer counter) {
        Objects.requireNonNull(actor, "buyer");
        Objects.requireNonNull(counter, "counter");
        if (!actor.getUsername().equalsIgnoreCase(counter.getBuyerUsername())) {
            throw new SecurityException("Only the request's buyer may respond to a counter");
        }
    }

    private static Buyer requireThreadBuyer(User sender, ChatThread thread) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(thread, "thread");
        if (!sender.getUsername().equalsIgnoreCase(thread.getBuyer().getUsername())) {
            throw new SecurityException("Only the thread's buyer may post a formal offer");
        }
        return thread.getBuyer();
    }

    private static void requireLive(Listing listing) {
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException(OFFERS_CLOSED_BANNER);
        }
    }

    private static void requireType(ChatThread thread, ListingType expected) {
        if (thread.getListing().getType() != expected) {
            throw new IllegalArgumentException("A " + thread.getListing().getType()
                    + " listing does not accept this kind of formal offer");
        }
    }

    private static void requireParticipant(User sender, ChatThread thread) {
        String username = sender.getUsername();
        boolean participant = username.equalsIgnoreCase(thread.getBuyer().getUsername())
                || username.equalsIgnoreCase(thread.getSeller().getUsername());
        if (!participant) {
            throw new SecurityException(
                    "Only the thread's buyer or the listing's seller may send messages");
        }
    }
}
