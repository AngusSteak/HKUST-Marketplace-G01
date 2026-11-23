package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Checks borrow due dates on a timer, independently of any screen (SRS UC-1.6 special
 * requirements). Every tick it sends a reminder to borrowers whose loan is nearly due and
 * auto-returns loans whose due date has passed. The first tick runs at start-up, so loans that
 * fell due while the app was closed are returned straight away.
 *
 * <p>The reminder goes out when the loan is within {@link #REMINDER_LEAD} of its due date, or
 * half-way through for loans shorter than twice that, so a 5-second loan still gets one.
 */
public final class LoanTimer {

    /** How long before the due date the reminder is sent, for loans long enough. */
    public static final Duration REMINDER_LEAD = Duration.ofHours(24);
    /** How often the timer checks; short so 5-second loans are handled on time. */
    public static final Duration TICK = Duration.ofSeconds(1);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final DummyDataService data;
    private final MyDealsService myDeals;
    private final Clock clock;
    private ScheduledExecutorService executor;

    public LoanTimer(DummyDataService data) {
        this(data, Clock.systemDefaultZone());
    }

    LoanTimer(DummyDataService data, Clock clock) {
        this.data = Objects.requireNonNull(data, "data");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.myDeals = new MyDealsService(data, clock);
    }

    /** Starts checking every {@link #TICK} on a background thread, beginning now. */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "loan-timer");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safeCheck, 0, TICK.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Stops the timer, waiting briefly for a check in progress, e.g. before the database closes. */
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    private void safeCheck() {
        try {
            check();
        } catch (RuntimeException e) {
            // Never let one bad tick stop the timer; the next tick tries again.
            System.err.println("[LoanTimer] check failed: " + e);
        }
    }

    /**
     * One pass over the active loans: remind the ones nearly due, auto-return the overdue ones.
     *
     * @return how many loans were auto-returned in this pass
     */
    public int check() {
        LocalDateTime now = LocalDateTime.now(clock);
        int returned = 0;
        for (Deal deal : data.deals().allDeals()) {
            if (deal.getDealType() != DealType.LENT || deal.getStatus() != DealStatus.ACTIVE) {
                continue;
            }
            if (!now.isBefore(deal.getDueDate())) {
                if (myDeals.autoReturn(deal)) {
                    returned++;
                }
            } else if (!now.isBefore(reminderTime(deal)) && !alreadyReminded(deal)) {
                data.notifications().notify(deal.getBuyerUsername(), NotificationCategory.BORROW_REMINDER,
                        "Reminder: '" + deal.getListing().getTitle() + "' is due back to "
                                + deal.getSellerDisplayName() + " by " + deal.getDueDate().format(WHEN) + ".",
                        new NotificationRef(NotificationRef.Target.DEAL, deal.getId()));
            }
        }
        return returned;
    }

    /** When the reminder is due: {@link #REMINDER_LEAD} before the due date, or half-way for short loans. */
    static LocalDateTime reminderTime(Deal deal) {
        Duration length = Duration.between(deal.getStartDate(), deal.getDueDate());
        Duration lead = length.dividedBy(2).compareTo(REMINDER_LEAD) < 0 ? length.dividedBy(2) : REMINDER_LEAD;
        return deal.getDueDate().minus(lead);
    }

    /** Whether the borrower already has a reminder for this loan (survives restarts, as notices are saved). */
    private boolean alreadyReminded(Deal deal) {
        String ref = NotificationRef.Target.DEAL + ":" + deal.getId();
        for (Notification n : data.notifications().forUser(deal.getBuyerUsername(), NotificationCategory.BORROW_REMINDER)) {
            if (n.getRelatedRef() != null && ref.equals(n.getRelatedRef().toString())) {
                return true;
            }
        }
        return false;
    }
}
