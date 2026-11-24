package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.Objects;

/**
 * Seller-only actions on the reviews of their own listings. Every method checks that the actor is
 * an active Seller who owns the review before changing anything, so the check holds even if a
 * screen offers the action by mistake.
 */
public final class SellerReviewService {

    public static final String REPLY_REQUIRED = "Please write a reply before saving.";
    public static final String ALREADY_REPLIED = "A reply has already been saved for this review.";
    public static final String ALREADY_FLAGGED = "This review is already flagged.";

    private final DummyDataService data;

    public SellerReviewService(DummyDataService data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Saves the seller's single reply to a review and notifies the buyer in one transaction, so a
     * failed notice cannot leave a reply behind. A review with a reply is read-only.
     *
     * @throws SecurityException if {@code actor} is not an active Seller who owns the review
     * @throws IllegalArgumentException with {@link #REPLY_REQUIRED} if {@code reply} is blank
     * @throws IllegalStateException with {@link #ALREADY_REPLIED} if the review already has a reply
     */
    public Review replyToReview(User actor, Review review, String reply) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(review, "review");
        if (!seller.getUsername().equalsIgnoreCase(review.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own review " + review.getId());
        }
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException(REPLY_REQUIRED);
        }
        if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
            throw new IllegalStateException(ALREADY_REPLIED);
        }
        String text = reply.trim();
        String oldReply = review.getSellerReply();
        try {
            data.database().inTransaction(() -> {
                review.setSellerReply(text);
                data.reviews().save(review);
                data.notifications().notify(review.getBuyerUsername(), NotificationCategory.REVIEW_REPLY,
                        seller.getDisplayName() + " replied to your review of '" + review.getListingTitle()
                                + "': " + text,
                        new NotificationRef(NotificationRef.Target.REVIEW, review.getId()));
            });
        } catch (RuntimeException e) {
            // The database rolled back; put the review object back the way it was too.
            review.setSellerReply(oldReply);
            throw e;
        }
        return review;
    }

    /**
     * Flags a review once for admin attention, saved through the review store in one transaction.
     * Flagging does not hide the review, change the derived rating or notify anyone. A review that
     * is already flagged is read-only for flagging.
     *
     * @throws SecurityException if {@code actor} is not an active Seller who owns the review
     * @throws IllegalStateException with {@link #ALREADY_FLAGGED} if the review is already flagged
     */
    public Review flagReview(User actor, Review review) {
        Seller seller = (Seller) AccessControl.require(actor, UserRole.SELLER);
        Objects.requireNonNull(review, "review");
        if (!seller.getUsername().equalsIgnoreCase(review.getSellerUsername())) {
            throw new SecurityException("@" + seller.getUsername() + " does not own review " + review.getId());
        }
        if (review.isFlagged()) {
            throw new IllegalStateException(ALREADY_FLAGGED);
        }
        boolean oldFlagged = review.isFlagged();
        try {
            data.database().inTransaction(() -> {
                review.setFlagged(true);
                data.reviews().save(review);
            });
        } catch (RuntimeException e) {
            // The database rolled back; put the review object back the way it was too.
            review.setFlagged(oldFlagged);
            throw e;
        }
        return review;
    }
}
