package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Message;
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
 * there is exactly one place that decides who may send and whether a thread is read-only.
 * Access control lives here, not in the UI and not in {@link ChatStore}.
 */
public final class ChatService {

    /** Shown on a non-live thread and used as the refusal message for a blocked send. */
    public static final String READ_ONLY_BANNER =
            "This listing is no longer live \u2014 chat is read-only.";

    private final DummyDataService data;

    public ChatService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Returns the one thread for this (Buyer, Listing) pair, creating it on first use. */
    public ChatThread openThread(Buyer buyer, Listing listing) {
        return data.chats().getOrCreate(buyer, listing);
    }

    /**
     * Sends a free-text message. Only the thread's Buyer or the Listing's Seller may send,
     * and only while the Listing is live. Empty content is rejected.
     *
     * @throws SecurityException if {@code sender} is not a participant of the thread
     * @throws IllegalStateException if the Listing is no longer live
     * @throws IllegalArgumentException if the content is blank
     */
    public Message sendText(User sender, ChatThread thread, String content) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(thread, "thread");
        requireParticipant(sender, thread);
        if (isReadOnly(thread)) {
            throw new IllegalStateException(READ_ONLY_BANNER);
        }
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
        requireLive(thread);
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
        requireLive(thread);
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
        requireLive(thread);
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
     * @throws IllegalStateException if the request is not pending or countered
     */
    public DealRequest declineOffer(User seller, DealRequest request) {
        requireListingSeller(seller, request);
        return data.deals().lifecycle().decline(request);
    }

    /** All messages in the thread, oldest (send-time) first. History is never removed. */
    public List<Message> history(ChatThread thread) {
        return data.chats().history(Objects.requireNonNull(thread, "thread"));
    }

    /** True when the Listing is no longer live, making the thread a read-only archive. */
    public boolean isReadOnly(ChatThread thread) {
        Objects.requireNonNull(thread, "thread");
        return thread.getListing().getStatus() != ListingStatus.LIVE;
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
            case TEXT_ONLY -> !message.isFormalOffer();
            case OFFERS_ONLY -> message.isFormalOffer();
        };
    }

    private static void requireListingSeller(User actor, DealRequest request) {
        Objects.requireNonNull(actor, "seller");
        Objects.requireNonNull(request, "request");
        if (!actor.getUsername().equalsIgnoreCase(request.getSellerUsername())) {
            throw new SecurityException(
                    "Only the listing's seller may accept or decline an offer");
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

    private void requireLive(ChatThread thread) {
        if (isReadOnly(thread)) {
            throw new IllegalStateException(READ_ONLY_BANNER);
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
