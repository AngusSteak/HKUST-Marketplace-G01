package com.hkust.marketplace.controllers.common;

import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.ReviewStore;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Comparator;
import java.util.List;

/**
 * A small read-only preview of one listing. It is used from screens that only link to a listing
 * (deal history, reviews, notifications) and do not render the full listing details themselves,
 * so a link can show the item without navigating away from the current screen.
 */
public final class ListingDetailDialog {

    private ListingDetailDialog() {
    }

    /**
     * Shows a read-only dialog with the key fields of {@code listing}.
     *
     * @param owner the window the dialog belongs to, or {@code null} for no owner
     * @param listing the listing to show; nothing happens when it is {@code null}
     */
    public static void show(Window owner, Listing listing) {
        if (listing == null) {
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Listing details");
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.setResizable(true);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        Label title = new Label(listing.getTitle());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: #1a1a2e;");

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", typeBadgeClass(listing));
        Label statusBadge = new Label(statusText(listing));
        statusBadge.getStyleClass().addAll("badge", statusBadgeClass(listing));
        HBox badges = new HBox(6, typeBadge, statusBadge);

        String priceText = listing.getPriceDisplay();
        if (listing instanceof LendListing lend) {
            priceText = priceText + " · " + DurationText.format(lend.getLendingDuration());
        }
        Label price = new Label(priceText);
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(6);
        addField(fields, 0, "Category", listing.getCategory().getLabel());
        addField(fields, 1, "Condition", listing.getCondition().getLabel());
        addField(fields, 2, "Meet-up", listing.getMeetupLocation().getLabel());
        addField(fields, 3, "Seller", listing.getSellerFullName());

        Label description = new Label(listing.getDescription() == null || listing.getDescription().isBlank()
                ? "No description." : listing.getDescription());
        description.setWrapText(true);

        content.getChildren().addAll(title, badges, price, fields, description);
        if (!listing.getPhotoPaths().isEmpty()) {
            content.getChildren().add(ListingPhotos.gallery(listing, 150, 110));
        }
        content.getChildren().add(reviewsSection(listing));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(540);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    /**
     * Every visible (non-hidden) review of a seller, newest first.
     *
     * @param sellerUsername the seller's username
     * @return the seller's reviews that are not hidden, most recent first; never {@code null}
     */
    public static List<Review> visibleSellerReviews(String sellerUsername) {
        return visibleSellerReviews(DummyDataService.getInstance().reviews(), sellerUsername);
    }

    /**
     * As above, reading from {@code reviews}. Visible so it can be tested with an in-memory store.
     *
     * @param reviews the review store to read
     * @param sellerUsername the seller's username
     * @return the seller's reviews that are not hidden, most recent first; never {@code null}
     */
    public static List<Review> visibleSellerReviews(ReviewStore reviews, String sellerUsername) {
        return reviews.forSeller(sellerUsername).stream()
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .toList();
    }

    private static VBox reviewsSection(Listing listing) {
        VBox section = new VBox(6);
        Label heading = new Label("Reviews of " + listing.getSellerFullName());
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        section.getChildren().add(heading);
        List<Review> reviews = visibleSellerReviews(listing.getSellerUsername());
        if (reviews.isEmpty()) {
            Label none = new Label("No reviews yet.");
            none.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            section.getChildren().add(none);
            return section;
        }
        for (Review review : reviews) {
            section.getChildren().add(reviewItem(review));
        }
        return section;
    }

    private static VBox reviewItem(Review review) {
        Label stars = new Label(stars(review.getRating()));
        stars.setStyle("-fx-text-fill: #A68137; -fx-font-weight: bold;");
        Label comment = new Label(review.getComment() == null ? "" : "\u201c" + review.getComment() + "\u201d");
        comment.setWrapText(true);
        Label by = new Label("\u2014 " + displayName(review.getBuyerUsername()));
        by.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        VBox item = new VBox(2, stars, comment, by);
        item.getStyleClass().add("notif-item");
        return item;
    }

    private static String stars(int rating) {
        return "\u2605".repeat(rating) + "\u2606".repeat(5 - rating);
    }

    private static String displayName(String username) {
        User user = DummyDataService.getInstance().users().find(username);
        return user == null ? username : user.getDisplayName();
    }

    private static void addField(GridPane grid, int row, String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 13px;");
        grid.addRow(row, nameLabel, valueLabel);
    }

    private static String statusText(Listing listing) {
        if (listing.isDeleted()) {
            return "Withdrawn";
        }
        return switch (listing.getStatus()) {
            case PENDING_REVIEW -> "Pending Review";
            case LIVE -> "Live";
            case REJECTED -> "Rejected";
            case RESERVED -> "Reserved";
            case SOLD -> "Sold";
            case LENT -> "Lent";
            case GIVEN -> "Given";
            case TAKEN_DOWN -> "Taken Down";
        };
    }

    private static String statusBadgeClass(Listing listing) {
        if (!listing.isDeleted()) {
            ListingStatus status = listing.getStatus();
            if (status == ListingStatus.LIVE) {
                return "badge-live";
            }
            if (status == ListingStatus.PENDING_REVIEW) {
                return "badge-pending";
            }
            if (status == ListingStatus.REJECTED) {
                return "badge-rejected";
            }
        }
        return "badge-sold";
    }

    private static String typeBadgeClass(Listing listing) {
        return switch (listing.getType()) {
            case SELL -> "badge-sold";
            case LEND -> "badge-pending";
            case GIVEAWAY -> "badge-live";
        };
    }
}
