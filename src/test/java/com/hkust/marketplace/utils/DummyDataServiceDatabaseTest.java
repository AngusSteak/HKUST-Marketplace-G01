package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Startup against a database file: seed once when empty, otherwise load what was saved (#59). */
class DummyDataServiceDatabaseTest {

    @TempDir
    Path dir;

    @Test
    void freshDatabaseIsSeededAndSavesTheDemoUsersAndListings() {
        DummyDataService data = DummyDataService.open(Database.openFile(file(), false));

        assertEquals(5, data.users().all().size());
        assertEquals(5, data.listings().all().size());
        assertEquals(5, data.database().query("SELECT COUNT(*) FROM users", r -> r.getInt(1)).get(0));
        assertEquals(5, data.database().query("SELECT COUNT(*) FROM listings", r -> r.getInt(1)).get(0));
        data.database().close();
    }

    @Test
    void reopeningKeepsChangesAndDoesNotSeedTwice() {
        DummyDataService first = DummyDataService.open(Database.openFile(file(), false));
        first.users().register(new Buyer("carol", "Carol Cheung", PasswordHasher.hash("Str0ng!Pass"), UserRole.BUYER));
        Listing fridge = first.listings().find("L104");
        fridge.setStatus(ListingStatus.LIVE);
        fridge.setReviewedAt(LocalDateTime.of(2026, 11, 23, 12, 0));
        first.listings().update(fridge);
        first.users().find("bob").setActive(false);
        first.users().save(first.users().find("bob"));
        first.database().close();

        DummyDataService second = DummyDataService.open(Database.openFile(file(), false));

        assertEquals(6, second.users().all().size(), "no second seed");
        assertEquals(5, second.listings().all().size());
        assertNotNull(second.users().find("carol"));
        assertTrue(PasswordHasher.verify("Str0ng!Pass", second.users().find("carol").getPasswordHash()));
        assertFalse(second.users().find("bob").isActive());
        assertEquals(ListingStatus.LIVE, second.listings().find("L104").getStatus());
        assertEquals(LocalDateTime.of(2026, 11, 23, 12, 0), second.listings().find("L104").getReviewedAt());
        assertEquals("Diana Ng", second.listings().find("L104").getSellerFullName());
        second.database().close();
    }

    @Test
    void resetStartsAgainFromTheDemoData() {
        DummyDataService first = DummyDataService.open(Database.openFile(file(), false));
        first.users().register(new Buyer("carol", "Carol Cheung", "hash", UserRole.BUYER));
        first.database().close();

        DummyDataService reset = DummyDataService.open(Database.openFile(file(), true));

        assertNull(reset.users().find("carol"));
        assertEquals(5, reset.users().all().size());
        reset.database().close();
    }

    @Test
    void aRestartKeepsEveryStoreConsistent() {
        DummyDataService first = DummyDataService.open(Database.openFile(file(), false));
        Buyer erin = new Buyer("erin", "Erin Lau", PasswordHasher.hash("Str0ng!Pass"), UserRole.BUYER);
        first.users().register(erin);
        ChatService chat = new ChatService(first);
        ChatThread thread = chat.openThread(erin, first.listings().find("L101"));
        chat.sendText(erin, thread, "Is the charger still available?");
        Message card = chat.submitFormalOffer(erin, thread, new BigDecimal("220"));
        Deal deal = chat.acceptOffer(first.users().find("charlie"), card.getDealRequest());
        first.favourites().toggleFavourite(erin, first.listings().find("L103"));
        first.notifications().notify("erin", NotificationCategory.OFFER_UPDATE,
                "Charlie accepted your offer.", new NotificationRef(NotificationRef.Target.DEAL, deal.getId()));
        Review review = first.reviews().all().get(0);
        review.setHidden(true);
        first.reviews().save(review);
        first.reports().fileReport(erin, first.listings().find("L103"), "Wrong photos");
        first.audit().recordTakeDown("admin", "L104");
        first.database().close();

        DummyDataService second = DummyDataService.open(Database.openFile(file(), false));

        Buyer erinAgain = (Buyer) second.users().find("erin");
        assertTrue(PasswordHasher.verify("Str0ng!Pass", erinAgain.getPasswordHash()));
        Deal dealAgain = second.deals().activeDealsForBuyer("erin").get(0);
        assertEquals(deal.getId(), dealAgain.getId());
        assertEquals(new BigDecimal("220.00"), ((PriceOffer) dealAgain.getRequest()).agreedPrice());
        assertEquals(ListingStatus.RESERVED, second.listings().find("L101").getStatus());
        assertSame(second.listings().find("L101"), dealAgain.getListing());
        ChatThread threadAgain = second.chats().forBuyer("erin").get(0);
        List<Message> history = second.chats().history(threadAgain);
        assertEquals(2, history.size());
        assertSame(dealAgain.getRequest(), history.get(1).getDealRequest());
        assertFalse(chat(second).acceptsOffers(threadAgain), "offers stay closed on the reserved listing");
        assertEquals(List.of("L103"), second.favourites().favouriteListingIds(erinAgain));
        assertEquals("DEAL:" + deal.getId(),
                second.notifications().forUser("erin").get(0).getRelatedRef().toString());
        assertTrue(second.reviews().all().get(0).isHidden());
        assertEquals(1, second.reports().openReports(second.listings().find("L103")).size());
        assertEquals("L104", second.audit().all().get(second.audit().all().size() - 1).getTarget());

        // the seeded rules still hold: alice's pending claim keeps the lamp reserved
        assertThrows(IllegalStateException.class, () -> second.deals().lifecycle()
                .submitClaim(erinAgain, second.listings().find("L102")));
        // and new ids continue after the saved ones
        assertEquals("D" + (Integer.parseInt(deal.getId().substring(1)) + 1), second.deals().lifecycle()
                .accept(second.deals().openRequestsByBuyer("bob").get(0)).getId());
        second.database().close();
    }

    private static ChatService chat(DummyDataService data) {
        return new ChatService(data);
    }

    private Path file() {
        return dir.resolve("data/marketplace.db");
    }
}
