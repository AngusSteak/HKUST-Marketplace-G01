package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AuditEntry;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.ReportStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.persistence.Database;
import com.hkust.marketplace.persistence.DatabaseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Notifications, reviews, listing reports and the audit log survive a reload (#58). */
class BoardPersistenceTest {

    private final Database db = Database.inMemory();
    private final DummyDataService seeded = seeded();

    @Test
    void notificationsReloadWithReferenceTimeAndReadFlag() {
        Notification offer = seeded.notifications().forUser("alice").get(0);
        seeded.notifications().markRead(offer, true);
        Notification announcement = seeded.notifications().notify("bob", NotificationCategory.ANNOUNCEMENT,
                "Welcome to HKUST Marketplace", null);

        DummyDataService reloaded = DummyDataService.open(db);

        Notification back = reloaded.notifications().forUser("alice").get(0);
        assertEquals(offer.getId(), back.getId());
        assertEquals(offer.getTitle(), back.getTitle());
        assertEquals(offer.getMessage(), back.getMessage());
        assertEquals(NotificationCategory.OFFER_UPDATE, back.getCategory());
        assertEquals("DEAL:D1", back.getRelatedRef().toString());
        assertEquals(offer.getTimestamp(), back.getTimestamp());
        assertTrue(back.isRead());
        Notification welcome = reloaded.notifications().forUser("bob").get(0);
        assertNull(welcome.getRelatedRef());
        assertFalse(welcome.isRead());

        Notification next = reloaded.notifications().notify("alice", NotificationCategory.LISTING_APPROVED,
                "Your listing is now live.", new NotificationRef(NotificationRef.Target.LISTING, "L101"));
        assertEquals("N" + (Integer.parseInt(announcement.getId().substring(1)) + 1), next.getId());
    }

    @Test
    void reviewsReloadAndKeepHiddenAndReply() {
        Review review = seeded.reviews().all().get(0);
        review.setHidden(true);
        review.setSellerReply("Thanks Bob!");
        seeded.reviews().save(review);

        DummyDataService reloaded = DummyDataService.open(db);

        Review back = reloaded.reviews().all().get(0);
        assertEquals(review.getId(), back.getId());
        assertEquals(5, back.getRating());
        assertEquals(review.getComment(), back.getComment());
        assertEquals("Old Chemistry Book", back.getListingTitle());
        assertEquals(review.getCreatedAt(), back.getCreatedAt());
        assertEquals("Thanks Bob!", back.getSellerReply());
        assertTrue(back.isHidden());
        assertTrue(reloaded.reviews().averageRating("charlie").isEmpty(), "hidden reviews stay out of the rating");

        Review another = new Review();
        another.setSellerUsername("diana");
        another.setBuyerUsername("alice");
        another.setRating(4);
        reloaded.reviews().add(another);
        assertEquals("R2", another.getId());
    }

    @Test
    void savingAnUnknownReviewIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> seeded.reviews().save(new Review()));
    }

    @Test
    void reportsReloadOpenAndResolved() {
        Buyer alice = (Buyer) seeded.users().find("alice");
        ListingReport spam = seeded.reports().fileReport(alice, seeded.listings().find("L101"), "Spam");
        ListingReport fake = seeded.reports().fileReport(alice, seeded.listings().find("L103"), "Fake");
        seeded.reports().resolveAll(seeded.listings().find("L103"), "Checked, it is genuine");

        DummyDataService reloaded = DummyDataService.open(db);

        List<ListingReport> back = reloaded.reports().all();
        assertEquals(List.of(spam.getId(), fake.getId()), back.stream().map(ListingReport::getId).toList());
        assertEquals(ReportStatus.OPEN, back.get(0).getStatus());
        assertEquals(spam.getReportedTime(), back.get(0).getReportedTime());
        assertSame(reloaded.listings().find("L101"), back.get(0).getListing());
        assertEquals(ReportStatus.RESOLVED, back.get(1).getStatus());
        assertEquals("Checked, it is genuine", back.get(1).getResolution());
        assertEquals(1, reloaded.reports().openReports(reloaded.listings().find("L101")).size());

        reloaded.reports().resolve(back.get(0), "Not spam");
        assertEquals(ReportStatus.RESOLVED, DummyDataService.open(db).reports().all().get(0).getStatus());
        ListingReport third = reloaded.reports().fileReport(alice, reloaded.listings().find("L101"), "Again");
        assertEquals("RPT3", third.getId());
    }

    @Test
    void aFailedResolutionSaveChangesNothingInMemory() {
        Buyer alice = (Buyer) seeded.users().find("alice");
        ListingReport spam = seeded.reports().fileReport(alice, seeded.listings().find("L101"), "Spam");
        ListingReport fake = seeded.reports().fileReport(alice, seeded.listings().find("L101"), "Fake");
        // Make the second row's update fail, so the transaction rolls back half-way through.
        db.update("CREATE TRIGGER fail_resolve BEFORE UPDATE ON listing_reports WHEN OLD.id = '"
                + fake.getId() + "' BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

        assertThrows(DatabaseException.class,
                () -> seeded.reports().resolveAll(seeded.listings().find("L101"), "Checked"));

        assertEquals(ReportStatus.OPEN, spam.getStatus());
        assertEquals(ReportStatus.OPEN, fake.getStatus());
        assertEquals(ReportStatus.OPEN, DummyDataService.open(db).reports().all().get(0).getStatus());
    }

    @Test
    void auditLogReloadsInOrder() {
        seeded.audit().recordTakeDown("admin", "L101");
        seeded.audit().recordAccountDeletion("admin", "@bob");
        List<AuditEntry> saved = seeded.audit().all();

        List<AuditEntry> back = DummyDataService.open(db).audit().all();

        assertEquals(List.of("Took down", "Deleted account"), back.stream().map(AuditEntry::getAction).toList());
        assertEquals(saved.get(0).getTime(), back.get(0).getTime());
        assertEquals("@bob", back.get(1).getTarget());
    }

    private DummyDataService seeded() {
        DummyDataService data = new DummyDataService(db);
        data.seedData();
        return data;
    }
}
