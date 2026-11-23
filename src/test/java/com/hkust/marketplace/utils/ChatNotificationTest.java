package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat About Listing (SRS UC-1.5): who is notified of messages and Formal Offers. */
class ChatNotificationTest {

    private final DummyDataService data = seeded();
    private final ChatService chat = new ChatService(data);
    private final Buyer bob = (Buyer) data.users().find("bob");
    private final User charlie = data.users().find("charlie");
    private final Listing charger = data.listings().find("L101");   // charlie's live Sale listing

    @Test
    void aBuyersMessageNotifiesTheSellerAndOpensTheThread() {
        ChatThread thread = chat.openThread(bob, charger);

        chat.sendText(bob, thread, "  Is this still available?  ");

        Notification notice = latest("charlie", NotificationCategory.CHAT_MESSAGE);
        assertEquals("Bob Wong about 'MacBook Pro Charger 61W': Is this still available?", notice.getMessage());
        assertEquals("THREAD:bob|L101", notice.getRelatedRef().toString());
        assertTrue(data.notifications().forUser("bob", NotificationCategory.CHAT_MESSAGE).isEmpty(),
                "the sender is not notified");
    }

    @Test
    void aSellersReplyNotifiesTheBuyer() {
        ChatThread thread = chat.openThread(bob, charger);

        chat.sendText(charlie, thread, "Yes, still available.");

        assertEquals("Charlie Lee about 'MacBook Pro Charger 61W': Yes, still available.",
                latest("bob", NotificationCategory.CHAT_MESSAGE).getMessage());
    }

    @Test
    void longMessagesArePreviewedShort() {
        ChatThread thread = chat.openThread(bob, charger);
        String longText = "x".repeat(200);

        chat.sendText(bob, thread, longText);

        String message = latest("charlie", NotificationCategory.CHAT_MESSAGE).getMessage();
        assertTrue(message.endsWith("x".repeat(ChatService.PREVIEW_LENGTH - 1) + "…"), message);
        assertEquals(longText, chat.history(thread).get(0).getContent(), "the message itself is kept whole");
    }

    @Test
    void anEmptyMessageIsNotSentAndNobodyIsNotified() {
        ChatThread thread = chat.openThread(bob, charger);

        assertThrows(IllegalArgumentException.class, () -> chat.sendText(bob, thread, "   "));

        assertTrue(chat.history(thread).isEmpty());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.CHAT_MESSAGE).isEmpty());
    }

    @Test
    void aFormalOfferFromChatIsValidatedPostedAndNotifiesTheSeller() {
        ChatThread thread = chat.openThread(bob, charger);

        Message card = chat.submitFormalOffer(bob, thread, new BigDecimal("210"));

        assertTrue(card.isFormalOffer());
        assertEquals(NotificationCategory.NEW_REQUEST, latest("charlie", NotificationCategory.NEW_REQUEST).getCategory());
        assertEquals("REQUEST:" + card.getDealRequest().getId(),
                latest("charlie", NotificationCategory.NEW_REQUEST).getRelatedRef().toString());
        assertThrows(IllegalArgumentException.class, () -> chat.submitFormalOffer(bob, thread, BigDecimal.ZERO));
        assertEquals(1, chat.history(thread).size(), "the invalid offer posted nothing");
    }

    @Test
    void aBorrowRequestFromChatNotifiesTheLender() {
        Listing calculator = data.listings().find("L103");
        ChatThread thread = chat.openThread(bob, calculator);
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        chat.submitFormalOffer(bob, thread, start, start.plusDays(10));

        assertTrue(latest("diana", NotificationCategory.NEW_REQUEST).getMessage().startsWith("Bob Wong asked to borrow"));
    }

    @Test
    void offersOnAListingThatIsNoLongerLiveAreRefusedButMessagesStillGoThrough() {
        Listing textbook = data.listings().find("L100");   // reserved
        Buyer alice = (Buyer) data.users().find("alice");
        ChatThread thread = chat.openThread(alice, textbook);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> chat.submitFormalOffer(alice, thread, new BigDecimal("100")));
        assertEquals(ChatService.OFFERS_CLOSED_BANNER, refused.getMessage());
        chat.sendText(alice, thread, "When can we meet?");
        assertEquals(List.of("When can we meet?"), chat.history(thread).stream()
                .map(Message::getContent).filter(c -> c.startsWith("When")).toList());
    }

    private Notification latest(String username, NotificationCategory category) {
        return data.notifications().forUser(username, category).get(0);
    }

    private static DummyDataService seeded() {
        DummyDataService service = new DummyDataService();
        service.seedData();
        return service;
    }
}
