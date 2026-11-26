package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    private static final Buyer ALICE = new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER);
    private static final Buyer BOB = new Buyer("bob", "Bob Wong", "hash", UserRole.BUYER);
    private static final Seller CHARLIE = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
    private static final Seller DIANA = new Seller("diana", "Diana Ng", "hash", UserRole.SELLER);

    private DummyDataService data;
    private ChatService chat;
    private Listing charlieListing;
    private Listing dianaListing;

    @BeforeEach
    void setUp() {
        data = new DummyDataService();
        chat = new ChatService(data);
        data.users().register(ALICE);
        data.users().register(BOB);
        data.users().register(CHARLIE);
        data.users().register(DIANA);
        charlieListing = listing("L1", CHARLIE);
        dianaListing = listing("L2", DIANA);
        data.listings().add(charlieListing);
        data.listings().add(dianaListing);
    }

    @Test
    void openingTheSamePairTwiceReturnsTheSameThread() {
        ChatThread first = chat.openThread(ALICE, charlieListing);
        ChatThread second = chat.openThread(ALICE, charlieListing);

        assertSame(first, second);
    }

    @Test
    void threadsAreSeparatePerBuyerAndListing() {
        ChatThread aliceOnCharlie = chat.openThread(ALICE, charlieListing);
        ChatThread aliceOnDiana = chat.openThread(ALICE, dianaListing);
        ChatThread bobOnCharlie = chat.openThread(BOB, charlieListing);

        assertNotSame(aliceOnCharlie, aliceOnDiana);
        assertNotSame(aliceOnCharlie, bobOnCharlie);
        assertEquals(2, chat.threadsForBuyer(ALICE).size());
        assertEquals(1, chat.threadsForBuyer(BOB).size());
    }

    @Test
    void sellerIsDerivedFromTheListing() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        assertSame(ALICE, thread.getBuyer());
        assertSame(charlieListing, thread.getListing());
        assertSame(CHARLIE, thread.getSeller());
    }

    @Test
    void buyerAndSellerCanBothSendText() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        Message fromBuyer = chat.sendText(ALICE, thread, "Is this still available?");
        Message fromSeller = chat.sendText(CHARLIE, thread, "Yes, it is.");

        assertEquals("alice", fromBuyer.getSenderUsername());
        assertEquals("charlie", fromSeller.getSenderUsername());
        assertFalse(fromBuyer.isFormalOffer());
        assertEquals(List.of("Is this still available?", "Yes, it is."),
                chat.history(thread).stream().map(Message::getContent).toList());
    }

    @Test
    void thirdPartyCannotSend() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        assertThrows(SecurityException.class, () -> chat.sendText(BOB, thread, "Hello?"));
        assertThrows(SecurityException.class, () -> chat.sendText(DIANA, thread, "Hello?"));
        assertTrue(chat.history(thread).isEmpty());
    }

    @Test
    void emptyMessagesAreRejected() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        assertThrows(IllegalArgumentException.class, () -> chat.sendText(ALICE, thread, ""));
        assertThrows(IllegalArgumentException.class, () -> chat.sendText(ALICE, thread, "   "));
        assertThrows(IllegalArgumentException.class, () -> chat.sendText(ALICE, thread, null));
        assertTrue(chat.history(thread).isEmpty());
    }

    @Test
    void historyIsInSendTimeOrder() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        chat.sendText(ALICE, thread, "one");
        chat.sendText(CHARLIE, thread, "two");
        chat.sendText(ALICE, thread, "three");

        List<Message> history = chat.history(thread);
        assertEquals(List.of("one", "two", "three"),
                history.stream().map(Message::getContent).toList());
        for (int i = 1; i < history.size(); i++) {
            assertFalse(history.get(i).getSentTime().isBefore(history.get(i - 1).getSentTime()));
        }
    }

    @Test
    void historySurvivesTheListingLeavingTheMarketplace() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "Before the sale");
        // A listing with a chat can't be deleted (the saved messages point to it); it is taken down.
        assertThrows(DatabaseException.class, () -> data.listings().remove(charlieListing.getId()));
        charlieListing.setStatus(ListingStatus.TAKEN_DOWN);
        data.listings().update(charlieListing);

        assertEquals(List.of("Before the sale"),
                chat.history(thread).stream().map(Message::getContent).toList());
    }

    @Test
    void messagesStayOpenAndHistoryIsKeptAfterTheListingIsSold() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "Before it sold");
        charlieListing.setStatus(ListingStatus.SOLD);

        assertFalse(chat.acceptsOffers(thread));
        chat.sendText(CHARLIE, thread, "Thanks, enjoy it!");
        chat.sendText(ALICE, thread, "Will do");

        assertEquals(List.of("Before it sold", "Thanks, enjoy it!", "Will do"),
                chat.history(thread).stream().map(Message::getContent).toList());
    }

    @Test
    void threadsAreScopedToEachParticipant() {
        ChatThread aliceOnCharlie = chat.openThread(ALICE, charlieListing);
        ChatThread bobOnDiana = chat.openThread(BOB, dianaListing);

        assertEquals(List.of(aliceOnCharlie), chat.threadsForBuyer(ALICE));
        assertEquals(List.of(bobOnDiana), chat.threadsForBuyer(BOB));
        assertEquals(List.of(aliceOnCharlie), chat.threadsForSeller(CHARLIE));
        assertEquals(List.of(bobOnDiana), chat.threadsForSeller(DIANA));
    }

    @Test
    void seededDataHasDemoConversationsForTheSeller() {
        DummyDataService seeded = new DummyDataService();
        seeded.seedData();
        ChatService seededChat = new ChatService(seeded);

        Seller charlie = (Seller) seeded.users().find("charlie");
        assertFalse(seededChat.threadsForSeller(charlie).isEmpty());
    }

    @Test
    void buyerPriceOfferCreatesOneLinkedPendingCard() {
        Listing sale = saleListing("S1", CHARLIE, "180");
        ChatThread thread = chat.openThread(ALICE, sale);

        Message card = chat.submitFormalOffer(ALICE, thread, new BigDecimal("150.50"));

        assertTrue(card.isFormalOffer());
        assertEquals("alice", card.getSenderUsername());
        PriceOffer request = assertInstanceOf(PriceOffer.class, card.getDealRequest());
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(new BigDecimal("150.50"), request.getOfferedPrice());
        assertSame(card, chat.history(thread).get(0));
        assertEquals(1, chat.history(thread).size());
        assertEquals(List.of(request), data.deals().allRequests());
        assertTrue(card.getContent().contains("Sale S1"));
        assertTrue(card.getContent().contains("HK$ 150.50"));
    }

    @Test
    void buyerBorrowRequestCreatesOneLinkedCardForLendListing() {
        Listing lend = lendListing("S2", CHARLIE, Duration.ofDays(7));
        ChatThread thread = chat.openThread(ALICE, lend);
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = start.plusDays(3);

        Message card = chat.submitFormalOffer(ALICE, thread, start, end);

        assertTrue(card.isFormalOffer());
        BorrowRequest request = assertInstanceOf(BorrowRequest.class, card.getDealRequest());
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(3, Duration.between(request.getStartDate(), request.getEndDate()).toDays());
        assertSame(card, chat.history(thread).get(0));
        assertTrue(card.getContent().contains("Lend S2"));
        assertTrue(card.getContent().contains("3 days"));
    }

    @Test
    void buyerClaimCreatesOneLinkedCardAndReservesTheGiveaway() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);

        Message card = chat.submitFormalOffer(ALICE, thread);

        assertTrue(card.isFormalOffer());
        Claim request = assertInstanceOf(Claim.class, card.getDealRequest());
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(ListingStatus.RESERVED, charlieListing.getStatus());
        assertFalse(chat.acceptsOffers(thread));
        chat.sendText(CHARLIE, thread, "Sure, pick it up tomorrow");
        assertSame(card, chat.history(thread).get(0));
        assertTrue(card.getContent().contains(charlieListing.getTitle()));
    }

    @Test
    void onlyTheThreadBuyerCanSubmitAFormalOffer() {
        Listing sale = saleListing("S3", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);

        assertThrows(SecurityException.class,
                () -> chat.submitFormalOffer(CHARLIE, thread, new BigDecimal("90")));
        assertThrows(SecurityException.class,
                () -> chat.submitFormalOffer(BOB, thread, new BigDecimal("90")));
        assertTrue(chat.history(thread).isEmpty());
        assertTrue(data.deals().allRequests().isEmpty());
    }

    @Test
    void formalOfferSummaryCarriesTermsAndTitleButNoStatus() {
        Listing sale = saleListing("S4", CHARLIE, "200");
        ChatThread thread = chat.openThread(ALICE, sale);

        Message card = chat.submitFormalOffer(ALICE, thread, new BigDecimal("180"));

        String summary = card.getContent().toLowerCase();
        assertTrue(summary.contains("180"));
        assertTrue(summary.contains("sale s4"));
        assertFalse(summary.contains("pending"));
        assertFalse(summary.contains("countered"));
        assertFalse(summary.contains("accepted"));
        assertFalse(summary.contains("declined"));
    }

    @Test
    void nonLiveListingAcceptsNoFormalOffer() {
        Listing sale = saleListing("S5", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        sale.setStatus(ListingStatus.SOLD);

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> chat.submitFormalOffer(ALICE, thread, new BigDecimal("90")));

        assertEquals(ChatService.OFFERS_CLOSED_BANNER, refusal.getMessage());
        assertTrue(chat.history(thread).isEmpty());
        assertTrue(data.deals().allRequests().isEmpty());
    }

    @Test
    void offerKindMustMatchTheListingType() {
        ChatThread giveawayThread = chat.openThread(ALICE, charlieListing);

        assertThrows(IllegalArgumentException.class,
                () -> chat.submitFormalOffer(ALICE, giveawayThread, new BigDecimal("10")));
        assertTrue(chat.history(giveawayThread).isEmpty());
        assertTrue(data.deals().allRequests().isEmpty());
    }

    @Test
    void searchMatchesContentCaseInsensitively() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "Selling a VINTAGE Camera");

        assertEquals(List.of("Selling a VINTAGE Camera"), contents(
                chat.search(ALICE, "vintage camera", MessageSearchFilter.ALL)));
        assertEquals(1, chat.search(ALICE, "VINTAGE", MessageSearchFilter.ALL).size());
        assertEquals(1, chat.search(ALICE, "vInTaGe", MessageSearchFilter.ALL).size());
        assertTrue(chat.search(ALICE, "not-there", MessageSearchFilter.ALL).isEmpty());
    }

    @Test
    void threadSearchLooksOnlyInThatThread() {
        ChatThread here = chat.openThread(ALICE, charlieListing);
        ChatThread elsewhere = chat.openThread(ALICE, dianaListing);
        chat.sendText(ALICE, here, "Is the Lamp still free?");
        chat.sendText(CHARLIE, here, "No, sorry");
        chat.sendText(CHARLIE, here, "Another lamp is coming");
        chat.sendText(ALICE, elsewhere, "Do you have a lamp too?");

        // Case-insensitive, oldest first like the thread itself, and nothing from the other thread.
        assertEquals(List.of("Is the Lamp still free?", "Another lamp is coming"),
                contents(chat.searchThread(ALICE, here, "LAMP", MessageSearchFilter.ALL)));
        assertTrue(chat.searchThread(CHARLIE, here, "camera", MessageSearchFilter.ALL).isEmpty());
    }

    @Test
    void threadSearchWithABlankKeywordKeepsEveryMessageOfTheChosenKind() {
        Listing sale = saleListing("S20", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "hello");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("80"));

        assertEquals(2, chat.searchThread(ALICE, thread, "  ", MessageSearchFilter.ALL).size());
        List<Message> offers = chat.searchThread(ALICE, thread, "", MessageSearchFilter.OFFERS_ONLY);
        assertEquals(1, offers.size());
        assertTrue(offers.get(0).isFormalOffer());
    }

    @Test
    void onlyTheThreadsBuyerAndSellerCanSearchIt() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "private note");

        assertThrows(SecurityException.class,
                () -> chat.searchThread(BOB, thread, "private", MessageSearchFilter.ALL));
        assertThrows(SecurityException.class,
                () -> chat.searchThread(DIANA, thread, "private", MessageSearchFilter.ALL));
    }

    @Test
    void allFilterReturnsTextAndFormalOffers() {
        Listing sale = saleListing("S10", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "Hello from Sale S10");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"));

        List<Message> all = chat.search(ALICE, "s10", MessageSearchFilter.ALL);

        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(Message::isFormalOffer));
        assertTrue(all.stream().anyMatch(message -> !message.isFormalOffer()));
    }

    @Test
    void textOnlyFilterExcludesFormalOffers() {
        Listing sale = saleListing("S11", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "Hello there");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"));

        List<Message> text = chat.search(ALICE, "hello", MessageSearchFilter.TEXT_ONLY);

        assertEquals(List.of("Hello there"), contents(text));
        assertTrue(text.stream().noneMatch(Message::isFormalOffer));
        assertTrue(chat.search(ALICE, "offer", MessageSearchFilter.TEXT_ONLY).isEmpty());
    }

    @Test
    void offersOnlyFilterReturnsOnlyFormalOffers() {
        Listing sale = saleListing("S12", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "Any offer?");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("80"));

        List<Message> offers = chat.search(ALICE, "", MessageSearchFilter.OFFERS_ONLY);

        assertEquals(1, offers.size());
        assertTrue(offers.get(0).isFormalOffer());
        assertEquals(1, chat.search(ALICE, "offer", MessageSearchFilter.OFFERS_ONLY).size());
    }

    @Test
    void formalOfferSummariesAreSearchableByPriceAndTitle() {
        Listing sale = saleListing("S13", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("77"));

        assertEquals(1, chat.search(ALICE, "77", MessageSearchFilter.OFFERS_ONLY).size());
        assertEquals(1, chat.search(CHARLIE, "sale s13", MessageSearchFilter.ALL).size());
    }

    @Test
    void buyerSearchOnlyCoversTheirOwnThreads() {
        ChatThread aliceThread = chat.openThread(ALICE, charlieListing);
        ChatThread bobThread = chat.openThread(BOB, charlieListing);
        chat.sendText(ALICE, aliceThread, "Private note from alice");
        chat.sendText(BOB, bobThread, "Private note from bob");

        assertEquals(List.of("Private note from alice"), contents(
                chat.search(ALICE, "private note", MessageSearchFilter.ALL)));
        assertEquals(List.of("Private note from bob"), contents(
                chat.search(BOB, "private note", MessageSearchFilter.ALL)));
    }

    @Test
    void sellerSearchOnlyCoversThreadsOnTheirListings() {
        ChatThread charlieThread = chat.openThread(ALICE, charlieListing);
        ChatThread dianaThread = chat.openThread(ALICE, dianaListing);
        chat.sendText(ALICE, charlieThread, "About the charlie item");
        chat.sendText(ALICE, dianaThread, "About the diana item");

        assertEquals(List.of("About the charlie item"), contents(
                chat.search(CHARLIE, "about", MessageSearchFilter.ALL)));
        assertEquals(List.of("About the diana item"), contents(
                chat.search(DIANA, "about", MessageSearchFilter.ALL)));
    }

    @Test
    void searchNeverLeaksThreadsTheUserDoesNotParticipateIn() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "Secret plans");

        assertTrue(chat.search(BOB, "secret", MessageSearchFilter.ALL).isEmpty());
        assertTrue(chat.search(DIANA, "secret", MessageSearchFilter.ALL).isEmpty());
    }

    @Test
    void searchResultsAreNewestFirst() throws InterruptedException {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "update first");
        Thread.sleep(3);
        chat.sendText(ALICE, thread, "update second");
        Thread.sleep(3);
        chat.sendText(ALICE, thread, "update third");

        ChatThread other = chat.openThread(ALICE, dianaListing);
        Thread.sleep(3);
        chat.sendText(ALICE, other, "update fourth");

        List<Message> results = chat.search(ALICE, "update", MessageSearchFilter.ALL);

        assertEquals(List.of("update fourth", "update third", "update second", "update first"),
                contents(results));
        for (int i = 1; i < results.size(); i++) {
            assertFalse(results.get(i).getSentTime().isAfter(results.get(i - 1).getSentTime()));
        }
    }

    @Test
    void blankKeywordWithTextOnlyReturnsNothing() {
        ChatThread thread = chat.openThread(ALICE, charlieListing);
        chat.sendText(ALICE, thread, "Hello");

        assertTrue(chat.search(ALICE, "", MessageSearchFilter.TEXT_ONLY).isEmpty());
        assertTrue(chat.search(ALICE, "   ", MessageSearchFilter.TEXT_ONLY).isEmpty());
        assertTrue(chat.search(ALICE, null, MessageSearchFilter.TEXT_ONLY).isEmpty());
    }

    @Test
    void blankKeywordWithAllReturnsNothing() {
        Listing sale = saleListing("S14", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "Hello");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"));

        assertTrue(chat.search(ALICE, "", MessageSearchFilter.ALL).isEmpty());
        assertTrue(chat.search(ALICE, "   ", MessageSearchFilter.ALL).isEmpty());
        assertTrue(chat.search(ALICE, null, MessageSearchFilter.ALL).isEmpty());
    }

    @Test
    void blankKeywordWithOffersOnlyListsEveryFormalOffer() {
        Listing sale = saleListing("S15", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        chat.sendText(ALICE, thread, "Hello");
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"));
        chat.submitFormalOffer(ALICE, thread, new BigDecimal("85"));

        List<Message> offers = chat.search(ALICE, "", MessageSearchFilter.OFFERS_ONLY);

        assertEquals(2, offers.size());
        assertTrue(offers.stream().allMatch(Message::isFormalOffer));
    }

    @Test
    void onlyTheListingSellerCanAcceptAnOffer() {
        Listing sale = saleListing("S16", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        DealRequest request = chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"))
                .getDealRequest();

        assertThrows(SecurityException.class, () -> chat.acceptOffer(ALICE, request));
        assertThrows(SecurityException.class, () -> chat.acceptOffer(DIANA, request));
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertTrue(data.deals().allDeals().isEmpty());
    }

    @Test
    void sellerAcceptCreatesDealAndClosesOffersButNotMessages() {
        Listing sale = saleListing("S17", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        DealRequest request = chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"))
                .getDealRequest();

        Deal deal = chat.acceptOffer(CHARLIE, request);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertSame(deal, data.deals().allDeals().get(0));
        assertSame(request, deal.getRequest());
        assertEquals(ListingStatus.RESERVED, sale.getStatus());
        assertFalse(chat.acceptsOffers(thread));
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> chat.submitFormalOffer(ALICE, thread, new BigDecimal("95")));
        assertEquals(ChatService.OFFERS_CLOSED_BANNER, refusal.getMessage());
        chat.sendText(CHARLIE, thread, "Meet at the Library at 3pm?");
        assertEquals(2, chat.history(thread).size());
    }

    @Test
    void sellerDeclineUpdatesStatusAndKeepsTheCardInHistory() {
        Listing sale = saleListing("S18", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        Message card = chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"));
        DealRequest request = card.getDealRequest();

        DealRequest declined = chat.declineOffer(CHARLIE, request);

        assertSame(request, declined);
        assertEquals(RequestStatus.DECLINED, request.getStatus());
        assertTrue(data.deals().allDeals().isEmpty());
        assertSame(card, chat.history(thread).get(0));
        assertTrue(chat.acceptsOffers(thread));
    }

    @Test
    void decliningAnotherSellersOfferIsRefused() {
        Listing sale = saleListing("S19", CHARLIE, "100");
        ChatThread thread = chat.openThread(ALICE, sale);
        DealRequest request = chat.submitFormalOffer(ALICE, thread, new BigDecimal("90"))
                .getDealRequest();

        assertThrows(SecurityException.class, () -> chat.declineOffer(DIANA, request));
        assertEquals(RequestStatus.PENDING, request.getStatus());
    }

    private static List<String> contents(List<Message> messages) {
        return messages.stream().map(Message::getContent).toList();
    }

    private static Listing listing(String id, Seller seller) {
        Listing listing = new GiveawayListing(seller, "Item " + id, "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY);
        listing.setId(id);
        listing.setStatus(ListingStatus.LIVE);
        return listing;
    }

    /** Saved like a real listing, so requests on it can be saved too. */
    private Listing saleListing(String id, Seller seller, String price) {
        Listing listing = new SaleListing(seller, "Sale " + id, "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, Money.of(price));
        listing.setId(id);
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }

    /** Saved like a real listing, so requests on it can be saved too. */
    private Listing lendListing(String id, Seller seller, Duration duration) {
        Listing listing = new LendListing(seller, "Lend " + id, "", Category.ELECTRONICS,
                Condition.USED, Location.LIBRARY, duration);
        listing.setId(id);
        listing.setStatus(ListingStatus.LIVE);
        data.listings().add(listing);
        return listing;
    }
}
