package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The exact filter rules behind the admin deals table and review list (#35, #36). */
class AdminFiltersTest {

    private final DummyDataService data = seeded();

    @Test
    void reviewScoreFilterMapsFiveStarsToOneStarCorrectly() {
        Review five = review("alice", "charlie", 5, "Great", false, false);
        Review one = review("bob", "charlie", 1, "Bad", false, false);
        Review three = review("bob", "diana", 3, "Okay", false, false);
        List<Review> all = List.of(five, one, three);

        assertEquals(List.of(five), filter(all, "", 1, "All reviews"));
        assertEquals(List.of(five), filter(all, "", 1, null));
        assertEquals(List.of(one), filter(all, "", 5, "All reviews"));
        assertEquals(List.of(three), filter(all, "", 3, "All reviews"));
        assertEquals(all, filter(all, "", 0, "All reviews"));
    }

    @Test
    void reviewVisibilityFiltersDoNotLeakIntoEachOther() {
        Review plain = review("alice", "charlie", 5, "Great", false, false);
        Review flagged = review("bob", "charlie", 4, "Flagged", true, false);
        Review hidden = review("bob", "charlie", 2, "Hidden", false, true);
        Review hiddenFlagged = review("alice", "diana", 1, "Gone", true, true);
        List<Review> all = List.of(plain, flagged, hidden, hiddenFlagged);

        assertEquals(List.of(flagged), filter(all, "", 0, "Flagged"));
        assertEquals(List.of(hidden, hiddenFlagged), filter(all, "", 0, "Hidden"));
        assertEquals(List.of(plain, flagged), filter(all, "", 0, "Visible"));
        assertEquals(all, filter(all, "", 0, "All reviews"));
    }

    @Test
    void reviewKeywordMatchesSellerBuyerOrComment() {
        Review review = review("bob", "charlie", 5, "Smooth textbook deal", false, false);

        assertTrue(AdminFilters.matchesReview(review, "CHarlie", 0, "All reviews"));
        assertTrue(AdminFilters.matchesReview(review, "bob", 0, "All reviews"));
        assertTrue(AdminFilters.matchesReview(review, "textbook", 0, "All reviews"));
        assertFalse(AdminFilters.matchesReview(review, "diana", 0, "All reviews"));
    }

    @Test
    void dealKeywordTypeAndStatusFilters() {
        Deal sold = deal("SOLD");
        Deal lent = deal("LENT");

        assertTrue(AdminFilters.matchesDeal(sold, "calculus", "All types", "All statuses", null, null));
        assertTrue(AdminFilters.matchesDeal(sold, "alice", "All types", "All statuses", null, null));
        assertTrue(AdminFilters.matchesDeal(sold, "charlie", "SOLD", sold.getStatus().name(), null, null));
        assertFalse(AdminFilters.matchesDeal(sold, "diana", "All types", "All statuses", null, null));
        assertFalse(AdminFilters.matchesDeal(sold, "", "LENT", "All statuses", null, null));
        assertFalse(AdminFilters.matchesDeal(sold, "", "All types", "CANCELLED", null, null));
        assertTrue(AdminFilters.matchesDeal(lent, "", "LENT", "All statuses", null, null));
    }

    @Test
    void dealDateRangeMatchesTheStartOrTheEndDate() {
        Deal sold = deal("SOLD");
        LocalDate start = sold.getStartDate().toLocalDate();

        assertTrue(AdminFilters.matchesDeal(sold, "", "All types", "All statuses", start, start));
        assertTrue(AdminFilters.matchesDeal(sold, "", "All types", "All statuses", null, start));
        assertFalse(AdminFilters.matchesDeal(sold, "", "All types", "All statuses", start.plusDays(1), null));
        assertFalse(AdminFilters.matchesDeal(sold, "", "All types", "All statuses", null, start.minusDays(1)));

        Deal loan = data.deals().allDeals().stream()
                .filter(d -> d.getDealType() == com.hkust.marketplace.models.DealType.LENT)
                .findFirst().orElseThrow();
        LocalDate finished = loan.getCompletedDate().toLocalDate();
        assertTrue(AdminFilters.matchesDeal(loan, "", "All types", "All statuses", finished, finished),
                "a range that only matches the end date still finds the deal");
    }

    @Test
    void deletedAccountsReadAsDeletedUserInHistory() {
        User charlie = data.users().find("charlie");
        assertEquals("Charlie Lee (@charlie)", AdminFilters.partyLabel(charlie, "charlie"));
        charlie.setDeleted(true);
        assertEquals("deleted user", AdminFilters.partyLabel(charlie, "charlie"));
        assertEquals("ghost", AdminFilters.partyLabel(null, "ghost"));
    }

    private List<Review> filter(List<Review> reviews, String keyword, int scoreIndex, String visibility) {
        return reviews.stream()
                .filter(r -> AdminFilters.matchesReview(r, keyword, scoreIndex, visibility))
                .toList();
    }

    private Review review(String buyer, String seller, int rating, String comment,
                          boolean flagged, boolean hidden) {
        Review review = new Review();
        review.setBuyerUsername(buyer);
        review.setSellerUsername(seller);
        review.setRating(rating);
        review.setComment(comment);
        review.setFlagged(flagged);
        review.setHidden(hidden);
        return review;
    }

    private Deal deal(String type) {
        return data.deals().allDeals().stream()
                .filter(d -> d.getDealType().name().equals(type))
                .findFirst().orElseThrow();
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService();
        service.seedData();
        return service;
    }
}
