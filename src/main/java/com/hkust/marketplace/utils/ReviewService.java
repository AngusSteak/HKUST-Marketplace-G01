package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReport;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manage Reviews (SRS UC-1.9) and Report Listing (UC-1.10) from the buyer's side. Each method
 * checks the buyer, validates the input before anything changes, saves through the stores and
 * notifies the other side in the same transaction.
 */
public final class ReviewService {

    /** SRS UC-1.9 A1. */
    public static final String NOT_ELIGIBLE = "This deal can't be reviewed: it isn't finished yet or you have already reviewed it.";
    /** SRS UC-1.9 A2. */
    public static final String RATING_MISSING = "Please choose a rating from 1 to 5 stars.";
    /** SRS UC-1.9 A2. */
    public static final String TEXT_MISSING = "Please write a few words about the seller.";
    /** SRS UC-1.10 A1. */
    public static final String REASON_MISSING = "Please give a reason for the report.";

    /** A deal can be reviewed once it is over: completed, or a loan that was returned. */
    private static final Set<DealStatus> FINISHED = Set.of(
            DealStatus.COMPLETED, DealStatus.RETURNED, DealStatus.AUTO_RETURNED);

    private final DummyDataService data;

    public ReviewService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Step 2: the buyer's finished deals that have no review yet, most recently finished first. */
    public List<Deal> reviewableDeals(User actor) {
        Buyer buyer = requireBuyer(actor);
        return data.deals().pastDealsForBuyer(buyer.getUsername()).stream()
                .filter(this::canBeReviewed)
                .toList();
    }

    /**
     * Step 3.1: stores a 1-5 star review of the deal's seller and notifies the seller; the seller's
     * average rating is derived from the stored reviews, so it updates at once.
     *
     * @throws SecurityException if {@code actor} is not the deal's active buyer
     * @throws IllegalStateException with {@link #NOT_ELIGIBLE} if the deal is not finished or already reviewed (A1)
     * @throws IllegalArgumentException with {@link #RATING_MISSING} or {@link #TEXT_MISSING} (A2)
     */
    public synchronized Review submitReview(User actor, Deal deal, int rating, String text) {
        Buyer buyer = requireBuyer(actor);
        Objects.requireNonNull(deal, "deal");
        if (!buyer.getUsername().equalsIgnoreCase(deal.getBuyerUsername())) {
            throw new SecurityException("This is not your deal.");
        }
        if (!canBeReviewed(deal)) {
            throw new IllegalStateException(NOT_ELIGIBLE);
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException(RATING_MISSING);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(TEXT_MISSING);
        }
        Review review = new Review();
        review.setDealId(deal.getId());
        review.setSellerUsername(deal.getSellerUsername());
        review.setBuyerUsername(buyer.getUsername());
        review.setListingTitle(deal.getListing().getTitle());
        review.setRating(rating);
        review.setComment(text.trim());
        data.database().inTransaction(() -> {
            data.reviews().add(review);
            data.notifications().notify(deal.getSellerUsername(), NotificationCategory.REVIEW_RECEIVED,
                    buyer.getDisplayName() + " rated you " + rating + "/5 for '" + deal.getListing().getTitle() + "'.",
                    new NotificationRef(NotificationRef.Target.REVIEW, review.getId()));
        });
        return review;
    }

    /** Step 3.2: every review the buyer has submitted, newest first, with any seller reply. */
    public List<Review> myReviews(User actor) {
        return data.reviews().byBuyer(requireBuyer(actor).getUsername());
    }

    /**
     * Seller flags (or unflags) a review they received, so the admin sees it first (SRS UC-3.8).
     * The review stays visible and keeps counting towards the seller's rating until the admin
     * hides it.
     */
    public synchronized void flagReview(User actor, Review review, boolean flagged) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(review, "review");
        if (!seller.getUsername().equalsIgnoreCase(review.getSellerUsername())) {
            throw new SecurityException("This review is not about you.");
        }
        review.setFlagged(flagged);
        data.database().inTransaction(() -> {
            data.reviews().save(review);
            if (flagged) {
                NotificationRef ref = new NotificationRef(NotificationRef.Target.REVIEW, review.getId());
                for (User admin : data.users().byRole(UserRole.ADMIN)) {
                    data.notifications().notify(admin.getUsername(), NotificationCategory.REPORTED_LISTING,
                            "Seller @" + seller.getUsername() + " flagged a review: " + review.getComment(), ref);
                }
            }
        });
    }

    /**
     * Report Listing (UC-1.10): stores an Open report and notifies every admin.
     *
     * @throws IllegalArgumentException with {@link #REASON_MISSING} if the reason is empty (A1)
     */
    public ListingReport reportListing(User actor, Listing listing, String reason) {
        Buyer buyer = requireBuyer(actor);
        Objects.requireNonNull(listing, "listing");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(REASON_MISSING);
        }
        ListingReport[] report = new ListingReport[1];
        data.database().inTransaction(() -> {
            report[0] = data.reports().fileReport(buyer, listing, reason);
            for (User admin : data.users().byRole(UserRole.ADMIN)) {
                data.notifications().notify(admin.getUsername(), NotificationCategory.REPORTED_LISTING,
                        buyer.getDisplayName() + " reported '" + listing.getTitle() + "': " + reason.trim(),
                        new NotificationRef(NotificationRef.Target.LISTING, listing.getId()));
            }
        });
        return report[0];
    }

    private boolean canBeReviewed(Deal deal) {
        return FINISHED.contains(deal.getStatus()) && data.reviews().forDeal(deal.getId()) == null;
    }

    private static Buyer requireBuyer(User actor) {
        return (Buyer) AccessControl.require(actor, UserRole.BUYER);
    }
}
