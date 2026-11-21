package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** In-memory deals. */
public class DealStore {

    private final List<Deal> deals = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(500);

    public List<Deal> forBuyer(String username) {
        return deals.stream()
                .filter(d -> d.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Deal> all() {
        return new ArrayList<>(deals);
    }

    public void add(Deal deal) {
        if (deal.getId() == null) {
            deal.setId("D" + idCounter.getAndIncrement());
        }
        deals.add(deal);
    }

    void seed(Listing listing) {
        Deal deal = new Deal();
        deal.setId("D" + idCounter.getAndIncrement());
        deal.setListingId(listing.getId());
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("charlie");
        deal.setDealType(ListingType.SELL);
        deal.setStatus("Accepted");
        deal.setOfferPrice(160.0);
        deals.add(deal);
    }
}
