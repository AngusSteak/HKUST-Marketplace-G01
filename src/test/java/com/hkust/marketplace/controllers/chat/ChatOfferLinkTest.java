package com.hkust.marketplace.controllers.chat;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.DealLifecycle;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOfferLinkTest {

    private Buyer alice;
    private Seller charlie;
    private User admin;
    private Listing charger;
    private DealLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        alice = new Buyer("alice", "Alice Wong", "hash", UserRole.BUYER);
        charlie = new Seller("charlie", "Charlie Chan", "hash", UserRole.SELLER);
        admin = new Admin("admin", "Admin", "hash", UserRole.ADMIN);
        charger = new SaleListing(charlie, "MacBook Pro Charger 61W", "Original Apple charger",
                Category.ELECTRONICS, Condition.USED, Location.LIBRARY, Money.of("180"));
        charger.setId("L101");
        charger.setStatus(ListingStatus.LIVE);
        lifecycle = new DealLifecycle();
    }

    private PriceOffer offer() {
        return lifecycle.submitOffer(alice, charger, new BigDecimal("170"));
    }

    @Test
    void sellerSeesLinkOnPendingOffer() {
        assertTrue(ChatViewBuilder.showsRequestLink(charlie, offer()));
    }

    @Test
    void sellerSeesLinkOnCounteredOffer() {
        PriceOffer pending = offer();
        lifecycle.counterOffer(pending, new BigDecimal("180"));
        assertTrue(ChatViewBuilder.showsRequestLink(charlie, pending));
    }

    @Test
    void sellerDoesNotSeeLinkOnAcceptedOffer() {
        PriceOffer pending = offer();
        lifecycle.accept(pending);
        assertFalse(ChatViewBuilder.showsRequestLink(charlie, pending));
    }

    @Test
    void sellerDoesNotSeeLinkOnDeclinedOffer() {
        PriceOffer pending = offer();
        lifecycle.decline(pending);
        assertFalse(ChatViewBuilder.showsRequestLink(charlie, pending));
    }

    @Test
    void buyerNeverSeesLink() {
        assertFalse(ChatViewBuilder.showsRequestLink(alice, offer()));
    }

    @Test
    void adminNeverSeesLink() {
        assertFalse(ChatViewBuilder.showsRequestLink(admin, offer()));
    }
}
