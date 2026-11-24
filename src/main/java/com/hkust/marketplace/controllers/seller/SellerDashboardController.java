package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import com.hkust.marketplace.forms.ListingFormComponent;
import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.LocalPhotoStorage;
import com.hkust.marketplace.utils.ReviewService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SellerService;
import com.hkust.marketplace.utils.SessionManager;
import com.hkust.marketplace.utils.UserStore;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;

public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnRequests, btnChats, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());
    private final SellerService sellerService = new SellerService(DummyDataService.getInstance());
    private final ReviewService reviewService = new ReviewService(DummyDataService.getInstance());

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

    @FXML
    private void showRequests() {
        openRequestsView(SellerRequestsController::render);
    }

    private void showRequestsFiltered(Listing listing) {
        openRequestsView(requests -> requests.renderFiltered(listing));
    }

    private void openRequestsView(Consumer<SellerRequestsController> render) {
        setActive(btnRequests, "Requests");
        contentPane.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/seller/SellerRequestsView.fxml"));
            Parent view = loader.load();
            SellerRequestsController requests = loader.getController();
            requests.setOnOpenChat(this::showThread);
            render.accept(requests);
            contentPane.getChildren().add(view);
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not open Requests: " + ex.getMessage()).showAndWait();
        }
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
        String reason = switch (l.getStatus()) {
            case REJECTED -> l.getRejectionReason();
            case TAKEN_DOWN -> l.getTakedownReason();
            default -> null;
        };
        if (reason != null && !reason.isBlank()) {
            Label reasonLabel = new Label("Reason: " + reason);
            reasonLabel.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 12px;");
            reasonLabel.setWrapText(true);
            info.getChildren().add(reasonLabel);
        }

        List<DealRequest> openRequests = DummyDataService.getInstance().deals().lifecycle()
                .requestsFor(l).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.COUNTERED)
                .toList();
        if (!openRequests.isEmpty()) {
            Hyperlink link = new Hyperlink("View " + openRequests.size() + " open offers");
            link.setOnAction(e -> showRequestsFiltered(l));
            info.getChildren().add(link);
        }

        Label status = new Label(l.getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (l.getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        boolean editAllowed = !l.isDeleted()
                && (l.getStatus() == ListingStatus.LIVE || l.getStatus() == ListingStatus.PENDING_REVIEW
                    || l.getStatus() == ListingStatus.REJECTED)
                && openRequests.isEmpty();
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setDisable(!editAllowed);
        if (!openRequests.isEmpty()) {
            editBtn.setTooltip(new Tooltip("Editing is unavailable while offers are open."));
            Label hint = new Label("Editing is unavailable while offers are open.");
            hint.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11px;");
            info.getChildren().add(hint);
        }
        editBtn.setOnAction(e -> showEditListing(l));

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> {
            int affected = openRequests.size();
            String message = affected == 0
                    ? "Withdraw this listing? There are no open offers."
                    : "Withdraw this listing?\n\n" + affected + " open offer"
                            + (affected == 1 ? "" : "s") + " will be declined and the buyer"
                            + (affected == 1 ? "" : "s") + " notified.";
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, message);
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    try {
                        sellerService.deleteListing(SessionManager.getCurrentUser(), l);
                    } catch (SecurityException | IllegalStateException ex) {
                        new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
                        return;
                    } catch (RuntimeException ex) {
                        new Alert(Alert.AlertType.ERROR,
                                "Could not withdraw the listing: " + ex.getMessage()).showAndWait();
                        return;
                    }
                    showMyListings();
                }
            });
        });

        boolean markSoldAllowed = !l.isDeleted()
                && l.getStatus() == ListingStatus.LIVE
                && l instanceof SaleListing;
        Button soldBtn = new Button("Mark sold");
        soldBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        soldBtn.setDisable(!markSoldAllowed);
        soldBtn.setOnAction(e -> {
            int affected = openRequests.size();
            String message = affected == 0
                    ? "Mark this listing as sold?\n\nAny open offers will be closed and their buyers notified."
                            + "\nThere are no open offers on this listing."
                    : "Mark this listing as sold?\n\n" + affected + " open offer"
                            + (affected == 1 ? "" : "s") + " will be closed and the buyer"
                            + (affected == 1 ? "" : "s") + " notified.";
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, message);
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    try {
                        sellerService.markSold(SessionManager.getCurrentUser(), l);
                    } catch (SecurityException | IllegalStateException ex) {
                        new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
                        return;
                    } catch (RuntimeException ex) {
                        new Alert(Alert.AlertType.ERROR,
                                "Could not mark the listing as sold: " + ex.getMessage()).showAndWait();
                        return;
                    }
                    showMyListings();
                }
            });
        });

        row.getChildren().addAll(info, status, editBtn, delBtn, soldBtn);
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

    private void showEditListing(Listing target) {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Seller seller)) {
            new Alert(Alert.AlertType.WARNING,
                    "You must be logged in as a seller to edit a listing.").showAndWait();
            showMyListings();
            return;
        }

        listingForm = new ListingFormComponent(() -> seller, state -> submitEdit(target, state));
        listingForm.setOnCancel(this::showMyListings);

        ScrollPane scroll = new ScrollPane(listingForm.getView());
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void submitEdit(Listing target, ListingFormState state) {
        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Seller seller)) {
            new Alert(Alert.AlertType.WARNING,
                    "You must be logged in as a seller to edit a listing.").showAndWait();
            return;
        }

        Map<String, String> errors = state.validate(seller);
        if (!errors.isEmpty()) {
            listingForm.showErrors(errors);
            return;
        }

        try {
            sellerService.updateListing(user, target, state, photoStorage);
        } catch (SecurityException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            return;
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not save the edit: " + ex.getMessage()).showAndWait();
            return;
        }

        new Alert(Alert.AlertType.INFORMATION,
                "Listing updated and sent back for review.").showAndWait();
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
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Review> reviews = DummyDataService.getInstance()
                .reviews().forSeller(user != null ? user.getUsername() : "");

        if (reviews.isEmpty()) {
            root.getChildren().add(new Label("No reviews yet."));
        } else {
            for (Review r : reviews) {
                VBox item = new VBox(6);
                item.getStyleClass().add("card");
                Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(r.getComment());
                comment.setWrapText(true);
                UserStore users = DummyDataService.getInstance().users();
                Label meta = new Label("by " + users.historicalLabel(r.getBuyerUsername())
                        + " · " + r.getListingTitle());
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                HBox actions = new HBox(8);
                Button reply = new Button("Reply");
                reply.getStyleClass().addAll("btn-outline", "btn-sm");
                reply.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Reply sent to buyer (demo).").showAndWait());
                Button flag = new Button(r.isFlagged() ? "Unflag" : "Flag");
                flag.getStyleClass().addAll("btn-danger", "btn-sm");
                flag.setOnAction(e -> {
                    try {
                        reviewService.flagReview(SessionManager.getCurrentUser(), r, !r.isFlagged());
                    } catch (SecurityException | IllegalArgumentException ex) {
                        new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
                    }
                    showReviews();
                });
                actions.getChildren().addAll(reply, flag);
                item.getChildren().addAll(stars, comment, meta, actions);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
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
