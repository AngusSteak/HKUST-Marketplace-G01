package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A buyer's report on a listing (SRS UC-1.10, UC-3.7). Reports start Open and are only ever
 * moved to Resolved with a resolution note by the admin.
 */
public class ListingReport {
    private final String id;
    private final Buyer buyer;
    private final Listing listing;
    private final String reason;
    private final LocalDateTime reportedTime;
    private ReportStatus status = ReportStatus.OPEN;
    private String resolution;

    public ListingReport(String id, Buyer buyer, Listing listing, String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.buyer = Objects.requireNonNull(buyer, "buyer");
        this.listing = Objects.requireNonNull(listing, "listing");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Report reason must not be blank");
        }
        this.reason = reason.trim();
        this.reportedTime = LocalDateTime.now();
    }

    public String getId() { return id; }
    public Buyer getBuyer() { return buyer; }
    public Listing getListing() { return listing; }
    public String getReason() { return reason; }
    public LocalDateTime getReportedTime() { return reportedTime; }
    public ReportStatus getStatus() { return status; }
    public String getResolution() { return resolution; }

    public String getReporterUsername() { return buyer.getUsername(); }

    public void resolve(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution must not be blank");
        }
        this.status = ReportStatus.RESOLVED;
        this.resolution = resolution.trim();
    }
}
