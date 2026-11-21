package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * A claim on a Giveaway listing. Only the first claim is recorded: it reserves the listing
 * while the seller accepts or declines it (SRS UC-1.4 S3, UC-2.5). Claims cannot be countered.
 */
public class Claim extends DealRequest {

    Claim(String id, Buyer buyer, Listing listing, LocalDateTime submittedTime) {
        super(id, buyer, listing, ListingType.GIVEAWAY, submittedTime);
    }

    @Override
    public DealType dealType() { return DealType.GIVEN; }
}
