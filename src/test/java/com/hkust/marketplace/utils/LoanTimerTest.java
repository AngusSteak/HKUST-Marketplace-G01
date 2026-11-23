package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.persistence.Database;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The due-date timer (SRS UC-1.6 special requirements): reminders and auto-return. */
class LoanTimerTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();

    @Test
    void remindsOnceWhenTheLoanIsNearlyDue() {
        Deal loan = loan(java.time.Duration.ofDays(10));

        timerAt(LoanTimer.reminderTime(loan).minusMinutes(1)).check();
        assertEquals(0, reminders());

        timerAt(LoanTimer.reminderTime(loan)).check();
        timerAt(LoanTimer.reminderTime(loan).plusHours(1)).check();
        assertEquals(1, reminders(), "only one reminder per loan");
        assertTrue(data.notifications().forUser("alice", NotificationCategory.BORROW_REMINDER).get(0).getMessage()
                .startsWith("Reminder: 'Graphing Calculator TI-84' is due back to Diana Ng by"));
        assertEquals(DealStatus.ACTIVE, loan.getStatus());
    }

    @Test
    void longLoansAreRemindedADayBeforeTheDueDate() {
        Deal longLoan = loan(java.time.Duration.ofDays(10));
        assertEquals(longLoan.getDueDate().minus(LoanTimer.REMINDER_LEAD), LoanTimer.reminderTime(longLoan));
    }

    @Test
    void shortLoansAreRemindedHalfWay() {
        Deal deal = data.deals().lifecycle().accept(requestBob(java.time.Duration.ofSeconds(10)));
        java.time.Duration half = java.time.Duration.between(deal.getStartDate(), deal.getDueDate()).dividedBy(2);
        assertEquals(deal.getDueDate().minus(half), LoanTimer.reminderTime(deal));
    }

    @Test
    void overdueLoansAreAutoReturnedOnceAndBothSidesAreTold() {
        Deal loan = loan(java.time.Duration.ofDays(10));
        LoanTimer late = timerAt(loan.getDueDate().plusSeconds(1));

        assertEquals(1, late.check());
        assertEquals(0, late.check(), "already returned");

        DummyDataService saved = DummyDataService.open(db);
        assertEquals(DealStatus.AUTO_RETURNED, saved.deals().allDeals().stream()
                .filter(d -> d.getId().equals(loan.getId())).findFirst().orElseThrow().getStatus());
        assertEquals(ListingStatus.LIVE, saved.listings().find("L103").getStatus());
        assertTrue(latestMessage("alice", NotificationCategory.BORROW_REMINDER).contains("has been marked as returned"));
        assertTrue(latestMessage("diana", NotificationCategory.DEAL_COMPLETED).contains("passed its due date"));
    }

    @Test
    void loansThatFellDueWhileTheAppWasClosedAreReturnedAtStartUp() {
        Deal loan = loan(java.time.Duration.ofDays(10));
        LocalDateTime afterDue = loan.getDueDate().plusDays(3);

        DummyDataService reopened = DummyDataService.open(db);   // the app starts again later
        int returned = new LoanTimer(reopened, clockAt(afterDue)).check();

        assertEquals(1, returned);
        assertEquals(ListingStatus.LIVE, DummyDataService.open(db).listings().find("L103").getStatus());
    }

    @Test
    void theRunningTimerAutoReturnsAFiveSecondLoanOnItsOwn() throws InterruptedException {
        Deal loan = data.deals().lifecycle().accept(requestBob(java.time.Duration.ofSeconds(5)));
        LoanTimer timer = new LoanTimer(data);
        timer.start();
        try {
            long deadline = System.currentTimeMillis() + 12_000;
            while (loan.getStatus() == DealStatus.ACTIVE && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
        } finally {
            timer.stop();
        }
        assertEquals(DealStatus.AUTO_RETURNED, loan.getStatus());
        assertEquals(ListingStatus.LIVE, data.listings().find("L103").getStatus());
        assertEquals(1, data.notifications().forUser("bob", NotificationCategory.BORROW_REMINDER).stream()
                .filter(n -> n.getMessage().startsWith("Reminder")).count(), "reminded before the due date");
    }

    @Test
    void stopWaitsForTheTimerAndASecondStopIsHarmless() {
        LoanTimer timer = new LoanTimer(data);
        timer.start();
        timer.stop();
        timer.stop();
        loan(java.time.Duration.ofDays(1));
        // after stop nothing runs: a check in this thread still works on the open database
        assertEquals(0, timerAt(LocalDateTime.now()).check());
    }

    @Test
    void listingsCanBeReadWhileTheTimerUpdatesThem() throws InterruptedException {
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                data.listings().all().forEach(l -> l.getStatus());
                data.listings().live();
            }
        });
        reader.start();
        for (int i = 0; i < 200; i++) {
            com.hkust.marketplace.models.Listing charger = data.listings().find("L101");
            data.listings().update(charger);
        }
        reader.join(10_000);
        assertEquals(5, data.listings().all().size());
    }

    private Deal loan(java.time.Duration length) {
        LocalDateTime start = LocalDateTime.now().plusMinutes(1).withNano(0);
        BorrowRequest request = new DealRequestService(data).submitBorrow(data.users().find("alice"),
                data.listings().find("L103"), start, start.plus(length));
        return data.deals().lifecycle().accept(request);
    }

    /** A loan for bob starting a second from now; the shortest the SRS allows is 5 seconds. */
    private BorrowRequest requestBob(java.time.Duration length) {
        LocalDateTime start = LocalDateTime.now().plusSeconds(1);
        // bob's seeded pending request on L103 is accepted-over; use a fresh one with the exact period.
        return new DealRequestService(data).submitBorrow(data.users().find("bob"), data.listings().find("L103"),
                start, start.plus(length));
    }

    private LoanTimer timerAt(LocalDateTime now) {
        return new LoanTimer(data, clockAt(now));
    }

    private static Clock clockAt(LocalDateTime now) {
        return Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    private long reminders() {
        return data.notifications().forUser("alice", NotificationCategory.BORROW_REMINDER).size();
    }

    private String latestMessage(String user, NotificationCategory category) {
        return data.notifications().forUser(user, category).get(0).getMessage();
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }
}
