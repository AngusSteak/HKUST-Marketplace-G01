package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.ListingSearch.Criteria;
import com.hkust.marketplace.utils.ListingSearch.SortOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Browse Marketplace rules (SRS UC-1.3): visibility, search, filters, sort and the price range. */
class ListingSearchTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 1, 9, 0);

    private final Seller charlie = new Seller("charlie", "Charlie Lee", "hash", UserRole.SELLER);
    private final Seller diana = new Seller("diana", "Diana Ng", "hash", UserRole.SELLER);

    // Posted on day 1..5; textbook is the oldest, fridge the newest.
    private final Listing textbook = sale(charlie, "Calculus Textbook", "Stewart 8th edition",
            Category.BOOKS_AND_NOTES, Condition.LIKE_NEW, "180", 1);
    private final Listing charger = sale(charlie, "MacBook Charger", "Original Apple 61W",
            Category.ELECTRONICS, Condition.USED, "250", 2);
    private final Listing calculator = lend(diana, "TI-84 Calculator", "Graphing calculator for loan",
            Category.ELECTRONICS, Condition.LIKE_NEW, 3);
    private final Listing lamp = giveaway(diana, "Desk Lamp", "LED, good condition",
            Category.FURNITURE, Condition.USED, 4);
    private final Listing fridge = sale(diana, "Mini Fridge", "Cleaning required",
            Category.FURNITURE, Condition.USED, "400", 5);
    private final List<Listing> all = List.of(textbook, charger, calculator, lamp, fridge);

    @Test
    void defaultShowsEveryLiveListingNewestFirst() {
        assertEquals(List.of(fridge, lamp, calculator, charger, textbook), search(Criteria.all()));
    }

    @Test
    void onlyLiveListingsAreShown() {
        charger.setStatus(ListingStatus.RESERVED);
        lamp.setStatus(ListingStatus.PENDING_REVIEW);
        fridge.setStatus(ListingStatus.TAKEN_DOWN);

        assertEquals(List.of(calculator, textbook), search(Criteria.all()));
    }

    @Test
    void listingsOfDisabledOrDeletedSellersAreHiddenWithoutChangingTheirStatus() {
        diana.setActive(false);
        assertEquals(List.of(charger, textbook), search(Criteria.all()));
        assertEquals(ListingStatus.LIVE, fridge.getStatus());

        diana.setActive(true);
        charlie.setDeleted(true);
        assertEquals(List.of(fridge, lamp, calculator), search(Criteria.all()));
    }

    @Test
    void keywordMatchesTitleOrDescriptionIgnoringCaseAndSpaces() {
        assertEquals(List.of(calculator, textbook), search(keyword("  CALCUL ")));   // both titles
        assertEquals(List.of(charger), search(keyword("apple")));                    // description only
        assertEquals(search(Criteria.all()), search(keyword("   ")));                // blank = no search
        assertEquals(List.of(textbook), search(keyword("calculus   TEXTBOOK")));     // repeated spaces
    }

    @Test
    void filtersCombineAndEachNarrowsTheResult() {
        assertEquals(List.of(calculator, charger),
                search(new Criteria(null, Category.ELECTRONICS, null, null, null, null, SortOrder.NEWEST)));
        assertEquals(List.of(fridge, charger, textbook),
                search(new Criteria(null, null, ListingType.SELL, null, null, null, SortOrder.NEWEST)));
        assertEquals(List.of(calculator, textbook),
                search(new Criteria(null, null, null, Condition.LIKE_NEW, null, null, SortOrder.NEWEST)));
        assertEquals(List.of(charger),
                search(new Criteria("mac", Category.ELECTRONICS, ListingType.SELL, Condition.USED,
                        null, null, SortOrder.NEWEST)));
    }

    @Test
    void priceRangeIsInclusiveAndKeepsOnlySaleListings() {
        assertEquals(List.of(charger, textbook), search(price("180", "250")));
        assertEquals(List.of(fridge), search(price("300", null)));
        assertEquals(List.of(charger, textbook), search(price(null, "250")));
        assertEquals(List.of(fridge, charger, textbook), search(price("0", null)), "lend and giveaway have no price");
    }

    @Test
    void invalidPriceRangeIsRefused() {
        IllegalArgumentException reversed = assertThrows(IllegalArgumentException.class,
                () -> search(price("300", "100")));
        assertEquals(ListingSearch.INVALID_PRICE_RANGE, reversed.getMessage());
        assertThrows(IllegalArgumentException.class, () -> search(price("-1", null)));
        assertThrows(IllegalArgumentException.class, () -> search(price(null, "-5")));
        assertEquals(List.of(charger), search(price("250", "250")), "min equal to max is fine");
    }

    @Test
    void priceBoxesAcceptPlainOrGroupedNumbersOnly() {
        assertEquals(null, ListingSearch.parsePrice("  "));
        assertEquals(new BigDecimal("50"), ListingSearch.parsePrice(" 50 "));
        assertEquals(new BigDecimal("120.50"), ListingSearch.parsePrice("120.50"));
        assertEquals(new BigDecimal("1200"), ListingSearch.parsePrice("1,200"));
        assertEquals(new BigDecimal("-5"), ListingSearch.parsePrice("-5"), "reported later as an invalid range");
        for (String bad : List.of("abc", "1,2,3", "12,00", "1.2.3", "HK$50", "5-")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ListingSearch.parsePrice(bad), bad);
            assertEquals(ListingSearch.INVALID_PRICE, e.getMessage());
        }
    }

    @Test
    void priceSortsPutUnpricedListingsLastNewestFirst() {
        assertEquals(List.of(textbook, charger, fridge, lamp, calculator),
                search(new Criteria(null, null, null, null, null, null, SortOrder.PRICE_LOW_HIGH)));
        assertEquals(List.of(fridge, charger, textbook, lamp, calculator),
                search(new Criteria(null, null, null, null, null, null, SortOrder.PRICE_HIGH_LOW)));
    }

    @Test
    void equalPricesAreNewestFirst() {
        Listing newerTextbook = sale(charlie, "Another Textbook", "", Category.BOOKS_AND_NOTES,
                Condition.USED, "180", 6);

        List<Listing> found = ListingSearch.search(List.of(textbook, newerTextbook),
                new Criteria(null, null, null, null, null, null, SortOrder.PRICE_LOW_HIGH));

        assertEquals(List.of(newerTextbook, textbook), found);
    }

    @Test
    void noMatchesGivesAnEmptyList() {
        assertTrue(search(keyword("unicorn")).isEmpty());
    }

    @Test
    void postedDateIsTheApprovalTimeWhenThereIsOne() {
        LocalDateTime approved = DAY.plusDays(10);
        textbook.setReviewedAt(approved);

        assertEquals(approved, ListingSearch.postedDate(textbook));
        assertEquals(DAY.plusDays(2), ListingSearch.postedDate(charger));
        assertEquals(textbook, search(Criteria.all()).get(0), "approval time drives newest first");
    }

    @Test
    void storeListingsCanBeSearchedDirectly() {
        DummyDataService data = new DummyDataService();
        data.seedData();

        List<Listing> found = ListingSearch.search(data.listings().all(), keyword("calculator"));

        assertEquals(List.of("L103"), found.stream().map(Listing::getId).toList(),
                "the seeded TI-84 is live; the pending fridge is not shown");
    }

    private List<Listing> search(Criteria criteria) {
        return ListingSearch.search(all, criteria);
    }

    private static Criteria keyword(String keyword) {
        return new Criteria(keyword, null, null, null, null, null, SortOrder.NEWEST);
    }

    private static Criteria price(String min, String max) {
        return new Criteria(null, null, null, null, min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), SortOrder.NEWEST);
    }

    private static Listing sale(Seller seller, String title, String description, Category category,
                                Condition condition, String price, int day) {
        return live(new SaleListing(seller, title, description, category, condition, Location.LIBRARY,
                Money.of(price)), day);
    }

    private static Listing lend(Seller seller, String title, String description, Category category,
                                Condition condition, int day) {
        return live(new LendListing(seller, title, description, category, condition, Location.LIBRARY,
                Duration.ofDays(90)), day);
    }

    private static Listing giveaway(Seller seller, String title, String description, Category category,
                                    Condition condition, int day) {
        return live(new GiveawayListing(seller, title, description, category, condition, Location.LIBRARY), day);
    }

    private static Listing live(Listing listing, int day) {
        listing.setId("T" + day);
        listing.setStatus(ListingStatus.LIVE);
        listing.setSubmittedDate(DAY.plusDays(day));
        return listing;
    }
}
