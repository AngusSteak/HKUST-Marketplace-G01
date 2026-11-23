package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DealHistorySearch.Criteria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deal History filters (SRS UC-1.6 S1 and A4). */
class DealHistorySearchTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    /** A clock the test moves forward, so each deal finishes on a known day. */
    private static final class MovableClock extends Clock {
        private Instant now = NOV_1.atTime(9, 0).toInstant(ZoneOffset.UTC);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void setDay(LocalDate day) { now = day.atTime(9, 0).toInstant(ZoneOffset.UTC); }
    }

    private final MovableClock clock = new MovableClock();
    private final DealLifecycle lifecycle = new DealLifecycle(clock);
    private final Buyer alice = new Buyer("alice", "Alice Chan", "hash", UserRole.BUYER);
    private final Seller charlie = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
    private final Seller diana = new Seller("diana", "Diana Ng", "hash", UserRole.SELLER);

    private final Deal textbook = sold(charlie, "Calculus Textbook", Category.BOOKS_AND_NOTES, 3);
    private final Deal charger = sold(charlie, "MacBook Charger", Category.ELECTRONICS, 10);
    private final Deal lamp = given(diana, "Desk Lamp", Category.FURNITURE, 20);
    private final List<Deal> all = List.of(lamp, charger, textbook);   // newest first, as Deal History shows

    @Test
    void noFiltersKeepsEveryDealInOrder() {
        assertEquals(all, DealHistorySearch.filter(all, Criteria.all()));
    }

    @Test
    void dateRangeIsInclusiveOnTheDayTheDealFinished() {
        assertEquals(List.of(charger, textbook), filter(new Criteria(day(3), day(10), null, null, null)));
        assertEquals(List.of(lamp), filter(new Criteria(day(11), null, null, null, null)));
        assertEquals(List.of(textbook), filter(new Criteria(null, day(3), null, null, null)));
        assertEquals(List.of(charger), filter(new Criteria(day(10), day(10), null, null, null)));
    }

    @Test
    void startAfterEndIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> filter(new Criteria(day(10), day(3), null, null, null)));
        assertEquals(DealHistorySearch.INVALID_DATE_RANGE, e.getMessage());
    }

    @Test
    void categorySellerAndKeywordCombine() {
        assertEquals(List.of(charger), filter(new Criteria(null, null, Category.ELECTRONICS, null, null)));
        assertEquals(List.of(charger, textbook), filter(new Criteria(null, null, null, "  CHARLIE ", null)));
        assertEquals(List.of(lamp), filter(new Criteria(null, null, null, "Diana Ng", null)));
        assertEquals(List.of(textbook), filter(new Criteria(null, null, null, "lee", "calculus")));
        assertEquals(List.of(), filter(new Criteria(null, null, Category.FURNITURE, "charlie", null)));
    }

    private List<Deal> filter(Criteria criteria) {
        return DealHistorySearch.filter(all, criteria);
    }

    private static LocalDate day(int dayOfMonth) {
        return NOV_1.withDayOfMonth(dayOfMonth);
    }

    private Deal sold(Seller seller, String title, Category category, int finishedOn) {
        Listing listing = live(new SaleListing(seller, title, "", category, Condition.USED, Location.LIBRARY,
                Money.of("100")), title);
        clock.setDay(NOV_1);
        Deal deal = lifecycle.accept(lifecycle.submitOffer(alice, listing, new BigDecimal("90")));
        clock.setDay(day(finishedOn));
        return lifecycle.complete(deal);
    }

    private Deal given(Seller seller, String title, Category category, int finishedOn) {
        Listing listing = live(new GiveawayListing(seller, title, "", category, Condition.USED, Location.LIBRARY), title);
        clock.setDay(NOV_1);
        Deal deal = lifecycle.accept(lifecycle.submitClaim(alice, listing));
        clock.setDay(day(finishedOn));
        return lifecycle.complete(deal);
    }

    private static Listing live(Listing listing, String id) {
        listing.setId(id);
        listing.setStatus(ListingStatus.LIVE);
        listing.setSubmittedDate(LocalDateTime.of(2026, 10, 1, 9, 0));
        return listing;
    }
}
