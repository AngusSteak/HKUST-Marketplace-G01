package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat threads and messages, including Formal Offers, survive a reload from the database. */
class ChatStorePersistenceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService seeded = seeded();

    @Test
    void textMessagesReloadWithSenderTimeAndOrder() {
        Buyer alice = (Buyer) seeded.users().find("alice");
        ChatThread before = seeded.chats().forBuyer("alice").get(0);
        List<Message> saved = seeded.chats().history(before);

        DummyDataService reloaded = DummyDataService.open(db);
        ChatThread after = reloaded.chats().forBuyer("alice").get(0);
        List<Message> back = reloaded.chats().history(after);

        assertEquals(before.getListing().getId(), after.getListing().getId());
        assertSame(reloaded.listings().find(after.getListing().getId()), after.getListing());
        assertEquals(saved.size(), back.size());
        for (int i = 0; i < saved.size(); i++) {
            assertEquals(saved.get(i).getContent(), back.get(i).getContent());
            assertEquals(saved.get(i).getSentTime(), back.get(i).getSentTime());
            assertEquals(saved.get(i).getSenderUsername(), back.get(i).getSenderUsername());
            assertFalse(back.get(i).isFormalOffer());
        }
        assertEquals(alice.getUsername(), after.getBuyer().getUsername());
    }

    @Test
    void formalOfferReloadsLinkedToTheReloadedRequest() {
        ChatService chat = new ChatService(seeded);
        Buyer bob = (Buyer) seeded.users().find("bob");
        ChatThread thread = chat.openThread(bob, seeded.listings().find("L101"));
        chat.sendText(bob, thread, "Would you take HK$200?");
        Message card = chat.submitFormalOffer(bob, thread, new BigDecimal("200"));
        chat.declineOffer(seeded.users().find("charlie"), card.getDealRequest());

        DummyDataService reloaded = DummyDataService.open(db);
        ChatThread back = reloaded.chats().forBuyer("bob").get(0);
        List<Message> history = reloaded.chats().history(back);

        assertEquals(2, history.size());
        assertNull(history.get(0).getDealRequest());
        Message offer = history.get(1);
        assertTrue(offer.isFormalOffer());
        assertEquals(card.getContent(), offer.getContent());
        assertSame(reloaded.deals().findRequest(card.getDealRequest().getId()), offer.getDealRequest());
        assertEquals(RequestStatus.DECLINED, offer.getDealRequest().getStatus());
    }

    @Test
    void threadListsPerBuyerAndSellerSurviveAReload() {
        DummyDataService reloaded = DummyDataService.open(db);

        assertEquals(seeded.chats().forBuyer("alice").size(), reloaded.chats().forBuyer("alice").size());
        assertEquals(seeded.chats().forSeller("charlie").size(), reloaded.chats().forSeller("charlie").size());
        Buyer alice = (Buyer) reloaded.users().find("alice");
        ChatThread existing = reloaded.chats().forBuyer("alice").get(0);
        assertSame(existing, reloaded.chats().getOrCreate(alice, existing.getListing()),
                "opening a saved pair returns the saved thread, not a second one");
    }

    @Test
    void messagesSentAfterAReloadAreSavedToo() {
        DummyDataService reloaded = DummyDataService.open(db);
        ChatThread thread = reloaded.chats().forBuyer("alice").get(0);
        Seller seller = thread.getSeller();
        reloaded.chats().send(thread, seller, "See you at the library at 3pm.");

        DummyDataService again = DummyDataService.open(db);
        List<Message> history = again.chats().history(again.chats().forBuyer("alice").get(0));
        assertEquals("See you at the library at 3pm.", history.get(history.size() - 1).getContent());
    }

    private DummyDataService seeded() {
        DummyDataService data = new DummyDataService(db);
        data.seedData();
        return data;
    }
}
