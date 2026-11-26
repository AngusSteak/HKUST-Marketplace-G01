package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Make Deal Request (SRS UC-1.4): offers, borrow requests and claims from the buyer's side. */
class DealRequestServiceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService data = seeded();
    private final DealRequestService requests = new DealRequestService(data);
    private final User alice = data.users().find("alice");
    private final User bob = data.users().find("bob");
    // Seeded: L101 charger (Sale, live), L103 TI-84 (Lend 90 days, live), L102 lamp (giveaway, claimed by alice).
    private final Listing charger = data.listings().find("L101");
    private final Listing calculator = data.listings().find("L103");

    @Test
    void offerIsRecordedPendingSavedAndTheSellerIsNotified() {
        PriceOffer offer = requests.submitOffer(bob, charger, new BigDecimal("200"));

        assertEquals(RequestStatus.PENDING, offer.getStatus());
        assertEquals(new BigDecimal("200.00"), offer.getOfferedPrice());
        assertTrue(data.deals().openRequestsByBuyer("bob").contains(offer), "shows in My Deals");
        assertEquals(RequestStatus.PENDING, DummyDataService.open(db).deals().findRequest(offer.getId()).getStatus());
        Notification notice = data.notifications().forUser("charlie").get(0);
        assertEquals(NotificationCategory.NEW_REQUEST, notice.getCategory());
        assertEquals("Bob Wong offered HK$ 200 for your listing 'MacBook Pro Charger 61W'.", notice.getMessage());
        assertEquals("REQUEST:" + offer.getId(), notice.getRelatedRef().toString());
    }

    @Test
    void invalidOffersAreRefusedWithAReasonAndNothingIsRecorded() {
        int before = data.deals().allRequests().size();
        for (String bad : List.of("0", "-5", "10.555")) {
            assertThrows(IllegalArgumentException.class,
                    () -> requests.submitOffer(bob, charger, new BigDecimal(bad)), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> requests.submitOffer(bob, charger, null));
        assertEquals(before, data.deals().allRequests().size());
        assertTrue(data.notifications().forUser("charlie", NotificationCategory.NEW_REQUEST).size() <= 1,
                "only the seeded notice");
    }

    @Test
    void borrowRequestWithinTheLendingDurationIsRecordedAndNotified() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        BorrowRequest request = requests.submitBorrow(alice, calculator, start, start.plusDays(30));

        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(start.plusDays(30), request.getEndDate());
        assertTrue(data.notifications().forUser("diana").get(0).getMessage().startsWith("Alice Chan asked to borrow"));
    }

    @Test
    void invalidBorrowPeriodsSayWhatIsWrong() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertReason("past", () -> requests.submitBorrow(alice, calculator, LocalDateTime.now().minusHours(1), start));
        assertReason("after the start", () -> requests.submitBorrow(alice, calculator, start, start.minusHours(1)));
        assertReason("5 seconds", () -> requests.submitBorrow(alice, calculator, start, start.plusSeconds(3)));
        assertReason("exceeds", () -> requests.submitBorrow(alice, calculator, start, start.plusDays(91)));
    }

    @Test
    void claimReservesTheGiveawayAndASecondClaimIsTold() {
        // Free the seeded lamp first: diana declines alice's claim.
        Listing lamp = data.listings().find("L102");
        data.deals().lifecycle().decline(data.deals().findRequest("Q2"));

        Claim claim = requests.claim(bob, lamp);

        assertEquals(ListingStatus.RESERVED, DummyDataService.open(db).listings().find("L102").getStatus());
        assertEquals("Bob Wong claimed your giveaway 'IKEA Desk Lamp'.",
                data.notifications().forUser("diana").get(0).getMessage());
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> requests.claim(alice, lamp));
        assertEquals(DealRequestService.ALREADY_CLAIMED, second.getMessage(), "already reserved when alice looks");
        assertEquals(List.of(claim), data.deals().requestsByBuyer("bob").stream()
                .filter(r -> r.getListing() == lamp).toList());
    }

    @Test
    void onlyOneOfManySimultaneousClaimsWins() throws Exception {
        Listing lamp = data.listings().find("L102");
        data.deals().lifecycle().decline(data.deals().findRequest("Q2"));
        List<User> buyers = List.of(alice, bob, alice, bob, alice, bob, alice, bob);
        ExecutorService pool = Executors.newFixedThreadPool(buyers.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = buyers.stream().map(b -> pool.submit(() -> {
            go.await();
            try {
                requests.claim(b, lamp);
                return "won";
            } catch (IllegalStateException e) {
                return e.getMessage();
            }
        })).toList();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        List<String> outcomes = new java.util.ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        assertEquals(1, outcomes.stream().filter("won"::equals).count(), outcomes.toString());
        assertTrue(outcomes.stream().filter(o -> !o.equals("won")).allMatch(o ->
                o.equals(DealRequestService.ALREADY_CLAIMED) || o.equals(DealRequestService.NOT_AVAILABLE)));
        assertEquals(1, data.deals().allRequests().stream()
                .filter(r -> r.getListing() == lamp && r.getStatus() == RequestStatus.PENDING).count());
    }

    @Test
    void aReservedGiveawayIsAlreadyClaimedButOtherUnavailableStatesAreNot() {
        Listing lamp = data.listings().find("L102");   // seeded: reserved by alice's pending claim

        assertEquals(DealRequestService.ALREADY_CLAIMED, assertThrows(IllegalStateException.class,
                () -> requests.claim(bob, lamp)).getMessage(), "someone else holds the reservation");

        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.GIVEN,
                ListingStatus.TAKEN_DOWN, ListingStatus.PENDING_REVIEW)) {
            lamp.setStatus(status);
            assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                    () -> requests.claim(bob, lamp)).getMessage(), status.name());
        }

        lamp.setStatus(ListingStatus.RESERVED);
        lamp.setDeleted(true);
        assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                () -> requests.claim(bob, lamp)).getMessage(), "a deleted giveaway hides its reservation");
    }

    @Test
    void aGiveawayFromAnInactiveOrDeletedSellerIsUnavailable() {
        Listing lamp = data.listings().find("L102");
        lamp.setStatus(ListingStatus.LIVE);
        User seller = lamp.getSeller();

        seller.setActive(false);
        assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                () -> requests.claim(bob, lamp)).getMessage(), "inactive seller");

        seller.setActive(true);
        seller.setDeleted(true);
        assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                () -> requests.claim(bob, lamp)).getMessage(), "deleted seller");
    }

    @Test
    void aLateRefusalIsReportedAsClaimedOnlyWhenTheGiveawayIsReserved() {
        // The lifecycle refused after our availability check; what changed decides the message.
        java.util.function.Supplier<Claim> refused = () -> {
            throw new IllegalStateException("Listing is not live");
        };
        Listing lamp = data.listings().find("L102");   // seeded: reserved by alice's claim
        assertEquals(DealRequestService.ALREADY_CLAIMED, assertThrows(IllegalStateException.class,
                () -> DealRequestService.record(refused, lamp)).getMessage());

        lamp.setStatus(ListingStatus.TAKEN_DOWN);
        assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                () -> DealRequestService.record(refused, lamp)).getMessage());
        assertEquals(DealRequestService.NOT_AVAILABLE, assertThrows(IllegalStateException.class,
                () -> DealRequestService.record(refused, null)).getMessage(), "offers and borrows");
    }

    @Test
    void listingsThatAreNotAvailableAreRefused() {
        Listing textbook = data.listings().find("L100");   // reserved by alice's accepted offer
        IllegalStateException reserved = assertThrows(IllegalStateException.class,
                () -> requests.submitOffer(bob, textbook, new BigDecimal("150")));
        assertEquals(DealRequestService.NOT_AVAILABLE, reserved.getMessage());

        User charlie = data.users().find("charlie");
        charlie.setActive(false);   // a disabled seller's listings are hidden
        assertThrows(IllegalStateException.class, () -> requests.submitOffer(bob, charger, new BigDecimal("150")));
    }

    @Test
    void onlyActiveBuyersCanMakeRequests() {
        User charlie = data.users().find("charlie");
        assertThrows(SecurityException.class, () -> requests.submitOffer(charlie, charger, new BigDecimal("150")));
        assertThrows(SecurityException.class, () -> requests.submitOffer(data.users().find("admin"), charger,
                new BigDecimal("150")));
        bob.setActive(false);
        assertThrows(SecurityException.class, () -> requests.submitOffer(bob, charger, new BigDecimal("150")));
        assertTrue(data.deals().openRequestsByBuyer("bob").stream().noneMatch(r -> r.getListing() == charger));
    }

    private static void assertReason(String expected, org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, action);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    private DummyDataService seeded() {
        DummyDataService service = new DummyDataService(db);
        service.seedData();
        return service;
    }

}
