package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/** A request to borrow a Lend listing for a period. The seller may counter with another period. */
public class BorrowRequest extends DealRequest {
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private LocalDateTime counterStart;
    private LocalDateTime counterEnd;

    BorrowRequest(String id, Buyer buyer, Listing listing, LocalDateTime startDate,
                  LocalDateTime endDate, LocalDateTime submittedTime) {
        super(id, buyer, listing, ListingType.LEND, submittedTime);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public DealType dealType() { return DealType.LENT; }

    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }

    /** The seller's counter start, or {@code null} if never countered. */
    public LocalDateTime getCounterStart() { return counterStart; }

    /** The seller's counter end, or {@code null} if never countered. */
    public LocalDateTime getCounterEnd() { return counterEnd; }

    /** The start that applies if accepted: the countered start if there is one. */
    public LocalDateTime agreedStart() {
        return counterStart != null ? counterStart : startDate;
    }

    /** The end that applies if accepted (the loan's due date). */
    public LocalDateTime agreedEnd() {
        return counterEnd != null ? counterEnd : endDate;
    }

    void setCounterPeriod(LocalDateTime start, LocalDateTime end) {
        this.counterStart = start;
        this.counterEnd = end;
    }
}
