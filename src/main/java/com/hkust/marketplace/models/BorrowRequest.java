package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/** A request to borrow a Lend listing for a period. The seller may counter with another period. */
public class BorrowRequest extends DealRequest {
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    /** Set only when the buyer accepts one of the seller's counters; {@code null} before that. */
    private LocalDateTime agreedCounterStart;
    private LocalDateTime agreedCounterEnd;

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

    /** The countered start the buyer agreed to, or {@code null} before a counter is accepted. */
    public LocalDateTime getAgreedCounterStart() { return agreedCounterStart; }

    /** The countered end the buyer agreed to, or {@code null} before a counter is accepted. */
    public LocalDateTime getAgreedCounterEnd() { return agreedCounterEnd; }

    /** The start that applies: the accepted counter if there is one, otherwise the request. */
    public LocalDateTime agreedStart() {
        return agreedCounterStart != null ? agreedCounterStart : startDate;
    }

    /** The end that applies (the loan's due date): the accepted counter if there is one. */
    public LocalDateTime agreedEnd() {
        return agreedCounterEnd != null ? agreedCounterEnd : endDate;
    }

    void agreeCounterPeriod(LocalDateTime start, LocalDateTime end) {
        this.agreedCounterStart = start;
        this.agreedCounterEnd = end;
    }
}
