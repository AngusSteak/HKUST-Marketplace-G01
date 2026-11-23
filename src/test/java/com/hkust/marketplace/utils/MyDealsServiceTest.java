package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manage My Deals (SRS UC-1.6): counter responses, returns and completion from the buyer's side. */
class MyDealsServiceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final MyDealsService myDeals = new MyDealsService(data);
    private final DealRequestService requests = new DealRequestService(data);
    private final User alice = data.users().find("alice");
    private final User bob = data.users().find("bob");
    private final Listing charger = data.listings().find("L101");      // charlie, Sale, live
    private final Listing calculator = data.listings().find("L103");   // diana, Lend 90 days, live

    // ---------- Counter responses ----------

    @Test
    void acceptingACounterOpensTheDealOnTheCounteredTermsAndDeclinesTheOthers() {
        PriceOffer bobs = requests.submitOffer(bob, charger, new BigDecimal("200"));
        PriceOffer alices = requests.submitOffer(alice, charger, new BigDecimal("190"));
        data.deals().lifecycle().counterOffer(bobs, new BigDecimal("230"));

        Deal deal = myDeals.acceptCounter(bob, bobs);

        assertEquals(new BigDecimal("230.00"), bobs.agreedPrice());
        assertEquals(RequestStatus.ACCEPTED, bobs.getStatus());
        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.RESERVED, saved.listings().find("L101").getStatus());
        assertEquals(RequestStatus.DECLINED, saved.deals().findRequest(alices.getId()).getStatus());
        assertEquals("Bob Wong accepted your counter-offer (HK$ 230) for 'MacBook Pro Charger 61W'.",
                latest("charlie", NotificationCategory.NEW_REQUEST).getMessage());
        assertEquals("DEAL:" + deal.getId(), latest("charlie", NotificationCategory.NEW_REQUEST).getRelatedRef().toString());
        assertEquals("REQUEST:" + alices.getId(), latest("alice", NotificationCategory.OFFER_UPDATE).getRelatedRef().toString());
    }

    @Test
    void acceptingABorrowCounterUsesTheCounteredDueDate() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        BorrowRequest request = requests.submitBorrow(alice, calculator, start, start.plusDays(30));
        data.deals().lifecycle().counterBorrow(request, start, start.plusDays(14));

        Deal deal = myDeals.acceptCounter(alice, request);

        assertEquals(start.plusDays(14), deal.getDueDate());
        assertEquals(ListingStatus.LENT, DummyDataService.open(db).listings().find("L103").getStatus());
    }

    @Test
    void aCounterOnAListingThatIsNoLongerAvailableIsDeclinedInstead() {
        PriceOffer offer = requests.submitOffer(bob, charger, new BigDecimal("200"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("230"));
        charger.setStatus(ListingStatus.TAKEN_DOWN);
        data.listings().update(charger);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> myDeals.acceptCounter(bob, offer));

        assertEquals(MyDealsService.COUNTER_NO_LONGER_VALID, e.getMessage());
        assertEquals(RequestStatus.DECLINED, DummyDataService.open(db).deals().findRequest(offer.getId()).getStatus());
        assertTrue(latest("charlie", NotificationCategory.NEW_REQUEST).getMessage().contains("could not be accepted"));
    }

    @Test
    void decliningACounterClosesTheRequestAndTellsTheSeller() {
        PriceOffer offer = requests.submitOffer(bob, charger, new BigDecimal("200"));
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("230"));

        myDeals.declineCounter(bob, offer);

        assertEquals(RequestStatus.DECLINED, DummyDataService.open(db).deals().findRequest(offer.getId()).getStatus());
        assertEquals(ListingStatus.LIVE, charger.getStatus());
        assertEquals("Bob Wong declined your counter-offer for 'MacBook Pro Charger 61W'.",
                latest("charlie", NotificationCategory.NEW_REQUEST).getMessage());
    }

    @Test
    void onlyTheRequestsBuyerCanRespondAndOnlyToACounter() {
        PriceOffer offer = requests.submitOffer(bob, charger, new BigDecimal("200"));

        assertThrows(IllegalStateException.class, () -> myDeals.acceptCounter(bob, offer), "not countered yet");
        data.deals().lifecycle().counterOffer(offer, new BigDecimal("230"));
        assertThrows(SecurityException.class, () -> myDeals.acceptCounter(alice, offer));
        assertThrows(SecurityException.class, () -> myDeals.declineCounter(data.users().find("charlie"), offer));
        assertEquals(RequestStatus.COUNTERED, offer.getStatus());
    }

    // ---------- Return and auto-return ----------

    @Test
    void returningALoanRelistsTheItemAndTellsTheLender() {
        Deal loan = loanToAlice();

        myDeals.returnItem(alice, loan);

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(DealStatus.RETURNED, saved.deals().allDeals().stream()
                .filter(d -> d.getId().equals(loan.getId())).findFirst().orElseThrow().getStatus());
        assertNotNull(loan.getCompletedDate());
        assertEquals(ListingStatus.LIVE, saved.listings().find("L103").getStatus());
        assertEquals("Alice Chan returned 'Graphing Calculator TI-84'. It is live again.",
                latest("diana", NotificationCategory.DEAL_COMPLETED).getMessage());
    }

    @Test
    void returningAfterTheDueDateReportsTheAutoReturn() {
        Deal loan = loanToAlice();
        Clock afterDue = Clock.fixed(loan.getDueDate().plusHours(1).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        MyDealsService late = new MyDealsService(data, afterDue);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> late.returnItem(alice, loan));

        assertEquals(MyDealsService.ALREADY_AUTO_RETURNED, e.getMessage());
        assertEquals(DealStatus.AUTO_RETURNED, loan.getStatus());
        assertEquals(ListingStatus.LIVE, DummyDataService.open(db).listings().find("L103").getStatus());
        assertEquals(NotificationCategory.BORROW_REMINDER, latest("alice", NotificationCategory.BORROW_REMINDER).getCategory());
        assertTrue(latest("diana", NotificationCategory.DEAL_COMPLETED).getMessage().contains("passed its due date"));
        assertEquals(MyDealsService.ALREADY_AUTO_RETURNED,
                assertThrows(IllegalStateException.class, () -> myDeals.returnItem(alice, loan)).getMessage());
        assertFalse(myDeals.autoReturn(loan), "a loan is auto-returned only once");
    }

    @Test
    void onlyTheBorrowerCanReturnAndOnlyLoansAreReturned() {
        Deal loan = loanToAlice();
        assertThrows(SecurityException.class, () -> myDeals.returnItem(bob, loan));

        Deal textbook = data.deals().activeDealsForBuyer("alice").stream()
                .filter(d -> d.getListing().getId().equals("L100")).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> myDeals.returnItem(alice, textbook));
    }

    // ---------- Mark complete ----------

    @Test
    void markingAPurchaseCompleteSellsTheListingAndTellsTheSeller() {
        Deal textbook = data.deals().activeDealsForBuyer("alice").stream()
                .filter(d -> d.getListing().getId().equals("L100")).findFirst().orElseThrow();   // seeded accepted offer

        myDeals.markComplete(alice, textbook);

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(ListingStatus.SOLD, saved.listings().find("L100").getStatus());
        assertEquals(DealStatus.COMPLETED, textbook.getStatus());
        assertNotNull(textbook.getCompletedDate());
        assertTrue(data.deals().pastDealsForBuyer("alice").contains(textbook), "appears in Deal History");
        assertEquals("Alice Chan received 'Calculus Textbook (Stewart)'. The deal is complete.",
                latest("charlie", NotificationCategory.DEAL_COMPLETED).getMessage());
        assertThrows(IllegalStateException.class, () -> myDeals.markComplete(alice, textbook), "only once");
    }

    @Test
    void markingAClaimCompleteGivesTheItemAway() {
        Listing lamp = data.listings().find("L102");
        Deal claim = data.deals().lifecycle().accept(data.deals().findRequest("Q2"));   // diana accepts alice's claim

        myDeals.markComplete(alice, claim);

        assertEquals(ListingStatus.GIVEN, DummyDataService.open(db).listings().find("L102").getStatus());
        assertEquals(lamp, claim.getListing());
    }

    @Test
    void loansAreNotMarkedCompleteAndStrangersCannotComplete() {
        Deal loan = loanToAlice();
        assertThrows(IllegalStateException.class, () -> myDeals.markComplete(alice, loan));
        Deal textbook = data.deals().activeDealsForBuyer("alice").get(0);
        assertThrows(SecurityException.class, () -> myDeals.markComplete(bob, textbook));
    }

    private Deal loanToAlice() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(1).withNano(0);
        BorrowRequest request = requests.submitBorrow(alice, calculator, start, start.plusDays(10));
        return data.deals().lifecycle().accept(request);
    }

    private Notification latest(String username, NotificationCategory category) {
        return data.notifications().forUser(username, category).get(0);
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
