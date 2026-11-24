package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import com.hkust.marketplace.forms.ListingFormComponent;
import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.LocalPhotoStorage;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SellerReviewService;
import com.hkust.marketplace.utils.SellerService;
import com.hkust.marketplace.utils.SessionManager;
import com.hkust.marketplace.utils.UserStore;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnChats, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private VBox reviewsRoot;
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());
    private final SellerService sellerService = new SellerService(DummyDataService.getInstance());
    private final SellerReviewService sellerReviewService = new SellerReviewService(DummyDataService.getInstance());

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Hello, " + user.getFullName());
        currentActiveBtn = btnListings;
        showMyListings();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DummyDataService.getInstance()
                .listings().bySeller(user != null ? user.getUsername() : "");

        if (myListings.isEmpty()) {
            Label empty = new Label("You have not posted any listings yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Listing l : myListings) {
                root.getChildren().add(createListingRow(l));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingRow(Listing l) {
        HBox row = new HBox(16);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label(l.getCategory() + " · " + l.getTypeDisplay() + " · " + l.getMeetupLocation());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label price = new Label(l.getPriceDisplay());
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 13px;");
        if (l instanceof LendListing lend) {
            price.setText(price.getText() + " · " + DurationText.format(lend.getLendingDuration()));
        }
        info.getChildren().addAll(title, meta, price);

        Label status = new Label(l.getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (l.getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Edit form would open here.\n(Demo placeholder)").showAndWait());

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> {
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete this listing?");
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    try {
                        new SellerService(DummyDataService.getInstance())
                                .deleteListing(SessionManager.getCurrentUser(), l);
                    } catch (SecurityException | IllegalStateException ex) {
                        new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
                    }
                    showMyListings();
                }
            });
        });

        row.getChildren().addAll(info, status, editBtn, delBtn);
        return row;
    }

    @FXML
    private void showChats() {
        setActive(btnChats, "Chats");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        if (!(SessionManager.getCurrentUser() instanceof Seller seller)) {
            root.getChildren().add(new Label("Log in as a seller to see your conversations."));
            contentPane.getChildren().add(root);
            return;
        }

        List<ChatThread> threads = chatService.threadsForSeller(seller);
        if (threads.isEmpty()) {
            Label empty = new Label("No buyer conversations yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (ChatThread thread : threads) {
                root.getChildren().add(createThreadRow(thread));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createThreadRow(ChatThread thread) {
        HBox row = new HBox(16);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(thread.getListing().getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label buyer = new Label("Buyer: " + thread.getBuyer().getFullName());
        buyer.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        List<Message> history = chatService.history(thread);
        String preview = history.isEmpty()
                ? "No messages yet"
                : history.get(history.size() - 1).getContent();
        Label last = new Label(preview);
        last.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        info.getChildren().addAll(title, buyer, last);

        Label status = new Label(thread.getListing().getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (thread.getListing().getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        Button open = new Button("Open");
        open.getStyleClass().addAll("btn-primary", "btn-sm");
        open.setOnAction(e -> showThread(thread));

        row.getChildren().addAll(info, status, open);
        return row;
    }

    private void showThread(ChatThread thread) {
        contentPane.getChildren().clear();

        Button back = new Button("\u2190 Back to Chats");
        back.getStyleClass().addAll("btn-outline", "btn-sm");
        back.setOnAction(e -> showChats());

        VBox root = new VBox(12);
        root.getChildren().addAll(back,
                ChatViewBuilder.build(chatService, SessionManager.getCurrentUser(), thread));
        contentPane.getChildren().add(root);
    }

    private final LocalPhotoStorage photoStorage = new LocalPhotoStorage();
    private ListingFormComponent listingForm;

    @FXML
    private void showCreateListing() {
        setActive(btnCreate, "Create New Listing");
        contentPane.getChildren().clear();

        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Seller seller)) {
            new Alert(Alert.AlertType.WARNING,
                    "You must be logged in as a seller to post a listing.").showAndWait();
            showMyListings();
            return;
        }

        listingForm = new ListingFormComponent(() -> seller, this::submitListing);
        listingForm.setOnCancel(this::showMyListings);

        ScrollPane scroll = new ScrollPane(listingForm.getView());
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void submitListing(ListingFormState state) {
        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Seller seller)) {
            new Alert(Alert.AlertType.WARNING,
                    "You must be logged in as a seller to post a listing.").showAndWait();
            return;
        }

        Map<String, String> errors = state.validate(seller);
        if (!errors.isEmpty()) {
            listingForm.showErrors(errors);
            return;
        }

        try {
            sellerService.createListing(user, state, photoStorage);
        } catch (SecurityException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            return;
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not save the listing: " + ex.getMessage()).showAndWait();
            return;
        }

        new Alert(Alert.AlertType.INFORMATION,
                "Listing submitted! Status: Pending Review.\nAdmin will review shortly.\n(Demo – saved locally)").showAndWait();
        showMyListings();
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(20);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        String sellerUsername = user != null ? user.getUsername() : "";
        List<Listing> myListings = DummyDataService.getInstance()
                .listings().bySeller(sellerUsername);

        // Metric cards
        OptionalDouble average = DummyDataService.getInstance().reviews().averageRating(sellerUsername);
        int reviewCount = DummyDataService.getInstance().reviews().visibleCount(sellerUsername);
        String averageRating = average.isPresent()
                ? String.format("%.1f", average.getAsDouble())
                : "No rating yet";
        HBox metrics = new HBox(16);
        metrics.getChildren().addAll(
                metricCard("Listings", String.valueOf(myListings.size()), "#003974"),
                metricCard("Avg Rating", averageRating, "#A68137"),
                metricCard("Reviews", String.valueOf(reviewCount), "#2e7d32"),
                metricCard("Views (demo)", "128", "#1565c0")
        );
        root.getChildren().add(metrics);

        // Bar chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Listings by Category (demo data)");
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(280);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Textbooks", 3));
        series.getData().add(new XYChart.Data<>("Electronics", 5));
        series.getData().add(new XYChart.Data<>("Furniture", 2));
        series.getData().add(new XYChart.Data<>("Others", 1));
        barChart.getData().add(series);

        // Pie chart
        PieChart pie = new PieChart();
        pie.setTitle("Listing Status Distribution");
        pie.setPrefHeight(280);
        pie.getData().add(new PieChart.Data("Live", 4));
        pie.getData().add(new PieChart.Data("Pending", 1));
        pie.getData().add(new PieChart.Data("Sold", 2));

        HBox charts = new HBox(20, barChart, pie);
        root.getChildren().add(charts);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        renderReviews();
    }

    private void renderReviews() {
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        reviewsRoot = root;

        User user = SessionManager.getCurrentUser();
        String sellerUsername = user != null ? user.getUsername() : "";
        List<Review> reviews = DummyDataService.getInstance().reviews().forSeller(sellerUsername);

        Label summary = new Label(ratingSummary(
                DummyDataService.getInstance().reviews().averageRating(sellerUsername),
                DummyDataService.getInstance().reviews().visibleCount(sellerUsername)));
        summary.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 14px;");
        root.getChildren().add(summary);

        if (reviews.isEmpty()) {
            Label empty = new Label("No reviews yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            UserStore users = DummyDataService.getInstance().users();
            for (Review r : reviews) {
                root.getChildren().add(createReviewRow(r, users));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createReviewRow(Review review, UserStore users) {
        VBox item = new VBox(6);
        item.getStyleClass().add("card");
        Label stars = new Label("★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating()));
        stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
        Label comment = new Label(review.getComment());
        comment.setWrapText(true);
        Label meta = new Label("by " + users.historicalLabel(review.getBuyerUsername())
                + " · " + review.getListingTitle());
        meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        item.getChildren().addAll(stars, comment, meta);

        if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
            Label reply = new Label("Your reply: " + review.getSellerReply());
            reply.setWrapText(true);
            reply.setStyle("-fx-text-fill: #003974; -fx-font-style: italic;");
            item.getChildren().add(reply);
        } else {
            item.getChildren().add(createReplyEditor(review));
        }
        item.getChildren().add(createFlagControl(review));
        return item;
    }

    private VBox createReplyEditor(Review review) {
        VBox editor = new VBox(4);
        TextArea input = new TextArea();
        input.setPromptText("Write your reply...");
        input.setPrefRowCount(2);
        input.setMaxWidth(Double.MAX_VALUE);
        Label message = new Label();
        message.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        Button save = new Button("Save Reply");
        save.getStyleClass().addAll("btn-primary", "btn-sm");
        save.setOnAction(e -> {
            try {
                sellerReviewService.replyToReview(SessionManager.getCurrentUser(), review, input.getText());
                renderReviews();
                showReviewFeedback("Reply saved.");
            } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
                message.setText(reviewActionError("reply to", ex));
            } catch (RuntimeException ex) {
                message.setText(reviewActionError("reply to", ex));
            }
        });
        editor.getChildren().addAll(input, message, save);
        return editor;
    }

    private HBox createFlagControl(Review review) {
        HBox control = new HBox(8);
        control.setAlignment(Pos.CENTER_LEFT);
        Button flag = new Button(flagButtonLabel(review.isFlagged()));
        flag.getStyleClass().addAll("btn-outline", "btn-sm");
        flag.setDisable(review.isFlagged());
        Label message = new Label();
        message.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        flag.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Flag this review for admin attention?");
            confirmation.showAndWait().ifPresent(answer -> {
                if (answer != ButtonType.OK) {
                    return;
                }
                try {
                    sellerReviewService.flagReview(SessionManager.getCurrentUser(), review);
                    renderReviews();
                    showReviewFeedback("Review flagged.");
                } catch (IllegalStateException | SecurityException ex) {
                    message.setText(reviewActionError("flag", ex));
                } catch (RuntimeException ex) {
                    message.setText(reviewActionError("flag", ex));
                }
            });
        });
        control.getChildren().addAll(flag, message);
        return control;
    }

    private void showReviewFeedback(String message) {
        if (reviewsRoot == null) {
            return;
        }
        Label feedback = new Label(message);
        feedback.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        reviewsRoot.getChildren().add(0, feedback);
    }

    static String ratingSummary(OptionalDouble average, int count) {
        String noun = count == 1 ? "review" : "reviews";
        if (average == null || average.isEmpty()) {
            return "No rating yet · " + count + " " + noun;
        }
        return String.format("Average rating: %.1f · %d %s", average.getAsDouble(), count, noun);
    }

    static String flagButtonLabel(boolean flagged) {
        return flagged ? "Already flagged" : "Flag";
    }

    static String reviewActionError(String action, RuntimeException ex) {
        if (ex instanceof SecurityException) {
            return "You do not have permission to " + action + " this review.";
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return ex.getMessage();
        }
        String detail = ex.getMessage();
        return "Could not " + action + " this review" + (detail == null ? "." : ": " + detail);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = SessionManager.getCurrentUser();
        List<Notification> notifs = DummyDataService.getInstance()
                .notifications().forUser(user != null ? user.getUsername() : "");
        if (notifs.isEmpty()) {
            root.getChildren().add(new Label("No notifications."));
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label t = new Label(n.getTitle());
                t.setStyle("-fx-font-weight: bold;");
                Label m = new Label(n.getMessage());
                m.setWrapText(true);
                m.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(t, m, meta);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(420);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.getChildren().add(new Label("Edit Seller Profile") {{ getStyleClass().add("section-title"); }});

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        TextArea bio = new TextArea(user instanceof Seller seller && seller.getBio() != null ? seller.getBio() : "");
        bio.setPrefRowCount(3);
        ComboBox<Location> meetup = new ComboBox<>(FXCollections.observableArrayList(Location.values()));
        if (user instanceof Seller seller) {
            seller.getPreferredMeetup().stream().findFirst().ifPresent(meetup::setValue);
        }
        meetup.setMaxWidth(Double.MAX_VALUE);

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-gold");
        save.setOnAction(e -> {
            if (user instanceof Seller seller && meetup.getValue() != null) {
                seller.setPreferredMeetup(EnumSet.of(meetup.getValue()));
            }
            new Alert(Alert.AlertType.INFORMATION, "Profile saved (demo).").showAndWait();
        });

        form.getChildren().addAll(
                new Label("Full Name"), name,
                new Label("New Password"), pass,
                new Label("Bio"), bio,
                new Label("Preferred Meet-up"), meetup,
                save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
