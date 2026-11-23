package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.ReportStatus;
import com.hkust.marketplace.persistence.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listing reports. Filing creates an Open report; only the admin resolves them. Reports are
 * saved to the {@code listing_reports} table when filed and when resolved; {@link #load}
 * rebuilds them at startup. Resolve through this store, not {@link ListingReport#resolve}
 * directly, so the resolution is saved.
 */
public class ReportStore {

    private final List<ListingReport> reports = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Database db;

    ReportStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public synchronized ListingReport fileReport(Buyer buyer, Listing listing, String reason) {
        ListingReport report = new ListingReport(
                "RPT" + idCounter.get(), buyer, listing, reason);
        db.update("INSERT INTO listing_reports (id, buyer_username, listing_id, reason, reported_time,"
                        + " status, resolution) VALUES (?, ?, ?, ?, ?, ?, ?)",
                report.getId(), report.getReporterUsername().toLowerCase(), listing.getId(),
                report.getReason(), report.getReportedTime(), report.getStatus(), report.getResolution());
        idCounter.incrementAndGet();
        // Only once saved, so a report filed inside a transaction that rolls back never appears.
        db.afterCommit(() -> {
            synchronized (this) {
                reports.add(report);
            }
        });
        return report;
    }

    /** Resolves one report with a note and saves it. */
    public synchronized void resolve(ListingReport report, String resolution) {
        if (!reports.contains(report)) {
            throw new IllegalArgumentException("Unknown report: " + report.getId());
        }
        resolveSaved(List.of(report), resolution);
    }

    /**
     * Open reports on one listing, in filing order (SRS UC-3.7). Matched by listing id, not
     * object identity, so reports survive the listing object being replaced (e.g. on edit).
     */
    public synchronized List<ListingReport> openReports(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        String listingId = Objects.requireNonNull(listing.getId(), "listing id");
        return reports.stream()
                .filter(r -> listingId.equals(r.getListing().getId()) && r.getStatus() == ReportStatus.OPEN)
                .toList();
    }

    /** Resolves every open report on the listing; returns how many were resolved. */
    public synchronized int resolveAll(Listing listing, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution must not be blank");
        }
        List<ListingReport> open = openReports(listing);
        resolveSaved(open, resolution);
        return open.size();
    }

    /**
     * Saves the reports as resolved in one transaction, then updates them in memory, so a
     * failed save changes nothing in either place.
     */
    private void resolveSaved(List<ListingReport> toResolve, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution must not be blank");
        }
        String note = resolution.trim();
        db.inTransaction(() -> toResolve.forEach(r -> db.update(
                "UPDATE listing_reports SET status = ?, resolution = ? WHERE id = ?",
                ReportStatus.RESOLVED, note, r.getId())));
        toResolve.forEach(r -> r.resolve(note));
    }

    public synchronized List<ListingReport> all() {
        return List.copyOf(reports);
    }

    /** Rebuilds saved reports in filing order and resumes the {@code RPT} id counter. */
    synchronized void load(UserStore users, ListingStore listings) {
        db.query("SELECT * FROM listing_reports ORDER BY CAST(substr(id, 4) AS INTEGER)", row -> {
            String id = row.getString("id");
            if (!(users.find(row.getString("buyer_username")) instanceof Buyer buyer)) {
                throw new IllegalStateException("Report " + id + " has unknown buyer");
            }
            Listing listing = listings.find(row.getString("listing_id"));
            if (listing == null) {
                throw new IllegalStateException("Report " + id + " is on unknown listing");
            }
            ListingReport report = new ListingReport(id, buyer, listing, row.getString("reason"),
                    Database.dateTime(row, "reported_time"), ReportStatus.valueOf(row.getString("status")),
                    row.getString("resolution"));
            reports.add(report);
            idCounter.set(Math.max(idCounter.get(), Integer.parseInt(id.substring(3)) + 1));
            return report;
        });
    }
}
