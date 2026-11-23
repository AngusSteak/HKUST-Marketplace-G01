package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.ReportStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory listing reports. Filing creates an Open report; only the admin resolves them. */
public class ReportStore {

    private final List<ListingReport> reports = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public ListingReport fileReport(Buyer buyer, Listing listing, String reason) {
        ListingReport report = new ListingReport(
                "RPT" + idCounter.getAndIncrement(), buyer, listing, reason);
        reports.add(report);
        return report;
    }

    /** Open reports on one listing, in filing order (SRS UC-3.7). */
    public List<ListingReport> openReports(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        return reports.stream()
                .filter(r -> r.getListing() == listing && r.getStatus() == ReportStatus.OPEN)
                .toList();
    }

    /** Resolves every open report on the listing; returns how many were resolved. */
    public int resolveAll(Listing listing, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution must not be blank");
        }
        List<ListingReport> open = openReports(listing);
        open.forEach(r -> r.resolve(resolution));
        return open.size();
    }

    public List<ListingReport> all() {
        return List.copyOf(reports);
    }
}
