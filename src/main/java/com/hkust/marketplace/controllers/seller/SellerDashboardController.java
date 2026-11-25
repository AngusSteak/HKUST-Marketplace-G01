package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import com.hkust.marketplace.controllers.common.ListingDetailDialog;
import com.hkust.marketplace.controllers.notifications.NotificationBoardView;
import com.hkust.marketplace.forms.ListingFormComponent;
import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.LocalPhotoStorage;
import com.hkust.marketplace.utils.PasswordVisibility;
import com.hkust.marketplace.utils.ProfileService;
import com.hkust.marketplace.utils.ReviewService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SellerReviewService;
import com.hkust.marketplace.utils.SellerService;
import com.hkust.marketplace.utils.SellerStats;
import com.hkust.marketplace.utils.SellerStatsService;
import com.hkust.marketplace.utils.SessionManager;
import com.hkust.marketplace.utils.UserStore;
import javafx.beans.property.SimpleStringProperty;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Seller dashboard controller: builds the listings, requests, stats, reviews, notifications and
 * profile screens in one content pane. All writes go through {@link SellerService} and the
 * other services; this class only collects input and renders results.
 */
public class SellerDashboardController {

    private static final String BACK_TO_CHATS = "\u2190 Back to Chats";
    private static final String BACK_TO_REQUESTS = "\u2190 Back to Requests";
    private static final String BACK_TO_NOTIFICATIONS = "\u2190 Back to Notifications";
    private static final DateTimeFormatter REVIEW_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final List<NotificationCategory> SELLER_CATEGORIES = List.of(
            NotificationCategory.LISTING_APPROVED, NotificationCategory.LISTING_REJECTED,
            NotificationCategory.NEW_REQUEST, NotificationCategory.REVIEW_RECEIVED,
            NotificationCategory.DEAL_COMPLETED, NotificationCategory.CHAT_MESSAGE,
            NotificationCategory.ANNOUNCEMENT);

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnRequests, btnChats, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private VBox reviewsRoot;
    private NotificationCategory notificationFilter;
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());
    private final SellerService sellerService = new SellerService(DummyDataService.getInstance());
    private final SellerReviewService sellerReviewService = new SellerReviewService(DummyDataService.getInstance());
    private final ReviewService reviewService = new ReviewService(DummyDataService.getInstance());
    private final SellerStatsService statsService = new SellerStatsService(DummyDataService.getInstance());

    /** Builds the dashboard after the FXML is loaded. */
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
        openRequestsView(SellerRequestsController::render, null);
    }

    private void showRequestsFiltered(Listing listing) {
        openRequestsView(requests -> requests.renderFiltered(listing), null);
    }

    private void openRequestsView(Consumer<SellerRequestsController> render, Runnable backToChat) {
        setActive(btnRequests, "Requests");
        contentPane.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/seller/SellerRequestsView.fxml"));
            Parent view = loader.load();
            SellerRequestsController requests = loader.getController();
            requests.setOnOpenChat(thread -> showThread(thread, () -> openRequestsView(render, backToChat)));
            requests.setOnBackToChat(backToChat);
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
        boolean deleteAllowed = !l.isDeleted()
                && (l.getStatus() == ListingStatus.LIVE || l.getStatus() == ListingStatus.PENDING_REVIEW
                    || l.getStatus() == ListingStatus.REJECTED);
        delBtn.setDisable(!deleteAllowed);
        if (!deleteAllowed) {
            delBtn.setTooltip(new Tooltip("Sold, reserved, lent, given or taken-down listings can no longer be changed."));
        }
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

        String markSoldReason = markSoldUnavailableReason(l);
        Button soldBtn = new Button("Mark sold");
        soldBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        soldBtn.setOnAction(e -> {
            // SRS UC-2.4 A2/A3: tell the seller why, instead of a disabled button with no message.
            if (markSoldReason != null) {
                new Alert(Alert.AlertType.WARNING, markSoldReason).showAndWait();
                return;
            }
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

    /**
     * Why the seller cannot mark {@code listing} as sold (SRS UC-2.4 A2/A3), or {@code null}
     * when the action is available. The dashboard shows this text so the seller is told the
     * reason instead of only seeing a disabled button.
     */
    static String markSoldUnavailableReason(Listing listing) {
        if (listing == null || listing.isDeleted()) {
            return SellerService.NOT_CHANGEABLE;
        }
        return switch (listing.getStatus()) {
            case RESERVED, SOLD, LENT, GIVEN, TAKEN_DOWN -> SellerService.NOT_CHANGEABLE;
            case PENDING_REVIEW, REJECTED -> SellerService.NOT_LIVE;
            case LIVE -> listing instanceof SaleListing ? null : SellerService.NOT_SALE_LISTING;
        };
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

    static String threadBackLabel(boolean fromRequests) {
        return fromRequests ? BACK_TO_REQUESTS : BACK_TO_CHATS;
    }

    private void showThread(ChatThread thread) {
        setActive(btnChats, "Chats");
        // Back to Chat re-opens this same view, so the title, sidebar and View request link return too.
        renderThread(thread, threadBackLabel(false), this::showChats,
                request -> openRequestsView(r -> r.renderFiltered(request.getListing()),
                        () -> showThread(thread)));
    }

    private void showThread(ChatThread thread, Runnable backAction) {
        renderThread(thread, threadBackLabel(true), backAction);
    }

    private void renderThread(ChatThread thread, String backLabel, Runnable backAction) {
        renderThread(thread, backLabel, backAction, null);
    }

    private void renderThread(ChatThread thread, String backLabel, Runnable backAction,
                              Consumer<DealRequest> onViewRequest) {
        contentPane.getChildren().clear();

        Button back = new Button(backLabel);
        back.getStyleClass().addAll("btn-outline", "btn-sm");
        back.setOnAction(e -> backAction.run());

        VBox root = new VBox(12);
        root.getChildren().addAll(back,
                ChatViewBuilder.build(chatService, SessionManager.getCurrentUser(), thread,
                        onViewRequest));
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
        listingForm.prefillFrom(target);

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
        SellerStats stats = statsService.statsFor(user);
        SellerStats.Totals totals = stats.totals();

        // Metric cards
        OptionalDouble average = totals.averageRating();
        String averageRating = average.isPresent()
                ? String.format("%.1f", average.getAsDouble())
                : "No rating yet";
        HBox metrics = new HBox(16);
        metrics.getChildren().addAll(
                metricCard("Total Views", String.valueOf(totals.views()), "#003974"),
                metricCard("Offers Received", String.valueOf(totals.offers()), "#A68137"),
                metricCard("Completed Deals", String.valueOf(totals.completed()), "#2e7d32"),
                metricCard("Average Rating", averageRating, "#1565c0")
        );
        root.getChildren().add(metrics);

        TableView<SellerStats.ListingStat> table =
                stats.listings().isEmpty() ? null : listingTable(stats.listings());
        Button showAll = new Button("Show all listings");
        showAll.getStyleClass().addAll("btn-outline", "btn-sm");
        showAll.setVisible(false);
        showAll.setManaged(false);
        if (table != null) {
            showAll.setOnAction(e -> {
                table.getItems().setAll(stats.listings());
                showAll.setVisible(false);
                showAll.setManaged(false);
            });
        }

        List<SellerStats.ListingStat> rowsByViews = stats.listings().stream()
                .sorted(Comparator.comparingInt(SellerStats.ListingStat::views).reversed())
                .toList();
        BarChart<Number, String> barChart = null;
        if (rowsByViews.isEmpty()) {
            // SRS UC-2.8 A1
            Label noChartData = new Label("No listings yet. Charts will appear once you post listings.");
            noChartData.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            root.getChildren().add(noChartData);
        } else {
            NumberAxis viewsOffersAxis = new NumberAxis();
            CategoryAxis listingAxis = new CategoryAxis();
            barChart = new BarChart<>(viewsOffersAxis, listingAxis);
            barChart.setTitle("Views and Offers per Listing");
            barChart.setPrefHeight(280);
            XYChart.Series<Number, String> viewsSeries = new XYChart.Series<>();
            viewsSeries.setName("Views");
            XYChart.Series<Number, String> offersSeries = new XYChart.Series<>();
            offersSeries.setName("Offers");
            // A horizontal bar chart draws its first category at the bottom, so add them in
            // ascending order to show the most-viewed listing at the top.
            for (SellerStats.ListingStat row : rowsByViews.reversed()) {
                String label = row.title().length() > 20 ? row.title().substring(0, 20) : row.title();
                viewsSeries.getData().add(bar(label, row.views(), row.title(), row.listingId()));
                offersSeries.getData().add(bar(label, row.offers(), row.title(), row.listingId()));
            }
            barChart.getData().addAll(List.of(viewsSeries, offersSeries));
            for (XYChart.Series<Number, String> series : barChart.getData()) {
                for (XYChart.Data<Number, String> data : series.getData()) {
                    whenBarShown(data, newNode -> {
                        newNode.setOnMouseClicked(event -> {
                            if (table == null) {
                                return;
                            }
                            String listingId = String.valueOf(data.getExtraValue());
                            stats.listings().stream()
                                    .filter(row -> row.listingId().equals(listingId))
                                    .findFirst()
                                    .ifPresent(row -> {
                                        table.getItems().setAll(List.of(row));
                                        showAll.setVisible(true);
                                        showAll.setManaged(true);
                                    });
                        });
                    });
                }
            }
        }

        // Pie chart
        Map<DealType, Integer> completedByType = stats.completedByType();
        int completedTotal = 0;
        for (DealType type : DealType.values()) {
            Integer count = completedByType.get(type);
            if (count != null) {
                completedTotal += count;
            }
        }
        PieChart pie = null;
        if (completedTotal == 0) {
            Label noPieData = new Label("No completed deals yet.");
            noPieData.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            root.getChildren().add(noPieData);
        } else {
            pie = new PieChart();
            pie.setTitle("Completed Deals by Type");
            pie.setPrefHeight(280);
            addPieSlice(pie, "Sold", completedByType.get(DealType.SOLD));
            addPieSlice(pie, "Lent", completedByType.get(DealType.LENT));
            addPieSlice(pie, "Given", completedByType.get(DealType.GIVEN));
        }

        HBox charts = new HBox(20);
        if (barChart != null) {
            charts.getChildren().add(barChart);
        }
        if (pie != null) {
            charts.getChildren().add(pie);
        }
        if (!charts.getChildren().isEmpty()) {
            root.getChildren().add(charts);
        }

        if (table != null) {
            root.getChildren().addAll(showAll, table);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private static void addPieSlice(PieChart pie, String label, Integer count) {
        if (count != null && count > 0) {
            pie.getData().add(new PieChart.Data(label, count));
        }
    }

    private static XYChart.Data<Number, String> bar(String label, int value, String title, String listingId) {
        XYChart.Data<Number, String> data = new XYChart.Data<>(value, label);
        data.setExtraValue(listingId);
        whenBarShown(data, node -> {
            node.setUserData(listingId);
            Tooltip.install(node, new Tooltip(title));
        });
        return data;
    }

    /**
     * Runs {@code action} on the bar's node now if the chart already created it, and on any node
     * it creates later. A chart creates bar nodes as soon as data is added, so a node-property
     * listener alone would miss them and the bars would get no handler.
     */
    static void whenBarShown(XYChart.Data<Number, String> data, Consumer<javafx.scene.Node> action) {
        if (data.getNode() != null) {
            action.accept(data.getNode());
        }
        data.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                action.accept(newNode);
            }
        });
    }

    private TableView<SellerStats.ListingStat> listingTable(List<SellerStats.ListingStat> rows) {
        TableView<SellerStats.ListingStat> table = new TableView<>();
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(List.of(
                statColumn("Listing", SellerStats.ListingStat::title),
                statColumn("Status", row -> row.status().name().replace('_', ' ')),
                statColumn("Outcome", row -> outcomeLabel(row.outcome())),
                statColumn("Views", row -> String.valueOf(row.views())),
                statColumn("Offers", row -> String.valueOf(row.offers())),
                statColumn("Completed", row -> String.valueOf(row.completed()))));
        table.getItems().setAll(rows);
        table.setPrefHeight(220);
        return table;
    }

    private static TableColumn<SellerStats.ListingStat, String> statColumn(
            String title, Function<SellerStats.ListingStat, String> value) {
        TableColumn<SellerStats.ListingStat, String> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        // Numbers are large; a text value such as "No rating yet" is smaller so it fits the card.
        String size = value.chars().anyMatch(Character::isLetter) ? "16px" : "28px";
        val.setStyle("-fx-font-size: " + size + "; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        val.setWrapText(true);
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
        Label meta = new Label(reviewMeta(review, users.historicalLabel(review.getBuyerUsername())));
        meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        item.getChildren().addAll(stars, comment, meta);
        Listing resolvedListing = reviewService.listingOf(review);
        if (resolvedListing != null) {
            Hyperlink link = new Hyperlink("View listing");
            link.setOnAction(e -> ListingDetailDialog.show(
                    contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), resolvedListing));
            item.getChildren().add(link);
        }

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
        String tooltip = flagTooltip(review.isFlagged());
        if (tooltip != null) {
            flag.setTooltip(new Tooltip(tooltip));
        }
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
                    if (!review.isFlagged()) {
                        reviewService.flagReview(SessionManager.getCurrentUser(), review, true);
                    }
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

    /** Meta line for a seller review: buyer, listing title and the review date (UC-2.9). */
    static String reviewMeta(Review review, String buyerLabel) {
        String base = "by " + buyerLabel + " · " + review.getListingTitle();
        return review.getCreatedAt() == null
                ? base
                : base + " · " + REVIEW_DATE_FORMAT.format(review.getCreatedAt());
    }

    /**
     * Label for the flag button: an already flagged review is awaiting admin moderation
     * (SRS UC-2.9 A3), so the button says so instead of just reporting it was flagged.
     */
    static String flagButtonLabel(boolean flagged) {
        return flagged ? "Awaiting moderation" : "Flag";
    }

    /** Tooltip explaining the flag button's state, or {@code null} when no hint is needed. */
    static String flagTooltip(boolean flagged) {
        return flagged ? "This review is already awaiting admin moderation." : null;
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

    static List<NotificationCategory> sellerCategories() {
        return SELLER_CATEGORIES;
    }

    /** Parses a THREAD ref id of the form {@code buyerUsername|listingId}, splitting at the first '|'. */
    static String[] parseThreadRef(String id) {
        if (id == null) {
            return null;
        }
        int bar = id.indexOf('|');
        if (bar < 0) {
            return null;
        }
        String buyer = id.substring(0, bar).trim();
        String listingId = id.substring(bar + 1).trim();
        if (buyer.isEmpty() || listingId.isEmpty()) {
            return null;
        }
        return new String[]{buyer, listingId};
    }

    /** Notification board (SRS UC-2.7): newest first, filter by category, open marks read. */
    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "";
        contentPane.getChildren().add(NotificationBoardView.build(username, SELLER_CATEGORIES,
                notificationFilter,
                f -> { notificationFilter = f; showNotifications(); },
                this::openNotification));
    }

    /**
     * Opens a notification (SRS UC-2.7 step 3.2): marks it read, then shows the related request,
     * deal or review details, opens a listing, chat thread or own profile, and falls back to the
     * matching list when that item is gone. A missing target is explained instead of throwing.
     */
    private void openNotification(Notification n) {
        DummyDataService data = DummyDataService.getInstance();
        if (!n.isRead()) {
            try {
                data.notifications().markRead(n, true);
            } catch (RuntimeException ex) {
                new Alert(Alert.AlertType.INFORMATION, "Could not update the notification.").showAndWait();
            }
        }
        showNotifications();
        NotificationRef ref = n.getRelatedRef();
        if (ref == null) {
            return;
        }
        switch (ref.getTarget()) {
            case LISTING -> {
                Listing listing = data.listings().find(ref.getId());
                if (listing == null) {
                    new Alert(Alert.AlertType.INFORMATION, "This listing no longer exists.").showAndWait();
                } else if (listing.isDeleted() || listing.getStatus() == ListingStatus.TAKEN_DOWN) {
                    ListingDetailDialog.show(
                            contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), listing);
                } else {
                    showMyListings();
                }
            }
            case REQUEST -> {
                DealRequest request = data.deals().findRequest(ref.getId());
                if (request == null) {
                    new Alert(Alert.AlertType.INFORMATION, "This request no longer exists.").showAndWait();
                    showRequests();
                } else {
                    showRequestNotification(request);
                }
            }
            case DEAL -> {
                Deal deal = data.deals().allDeals().stream()
                        .filter(d -> d.getId().equals(ref.getId()))
                        .findFirst()
                        .orElse(null);
                if (deal == null) {
                    showRequests();
                } else {
                    showDealNotification(deal);
                }
            }
            case THREAD -> {
                User user = SessionManager.getCurrentUser();
                if (!(user instanceof Seller seller)) {
                    return;
                }
                String[] parts = parseThreadRef(ref.getId());
                ChatThread target = null;
                if (parts != null) {
                    for (ChatThread thread : chatService.threadsForSeller(seller)) {
                        if (thread.getListing().getId().equals(parts[1])
                                && thread.getBuyer().getUsername().equalsIgnoreCase(parts[0])) {
                            target = thread;
                            break;
                        }
                    }
                }
                if (target == null) {
                    new Alert(Alert.AlertType.INFORMATION, "This conversation no longer exists.").showAndWait();
                } else {
                    renderThread(target, BACK_TO_NOTIFICATIONS, this::showNotifications);
                }
            }
            case REVIEW -> {
                Review review = data.reviews().all().stream()
                        .filter(r -> r.getId().equals(ref.getId()))
                        .findFirst()
                        .orElse(null);
                if (review == null) {
                    showReviews();
                } else {
                    showReviewNotification(review);
                }
            }
            case USER -> {
                User user = SessionManager.getCurrentUser();
                if (user != null && user.getUsername().equalsIgnoreCase(ref.getId())) {
                    showProfile();
                }
            }
        }
    }

    /** Read-only details of the request a notification points at (SRS UC-2.7 step 3.2.1). */
    private void showRequestNotification(DealRequest request) {
        String text = requestNotificationText(request.getId(), request.dealType(), request.getStatus(),
                request.getListing().getTitle(), request.getBuyerDisplayName(),
                requestTermsText(request), request.getSubmittedTime());
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Request details");
        alert.setHeaderText(request.getListing().getTitle());
        alert.getDialogPane().setContent(notificationBody(text));
        alert.showAndWait();
    }

    /** Read-only details of the deal a notification points at (SRS UC-2.7 step 3.2.1). */
    private void showDealNotification(Deal deal) {
        LocalDateTime dueOrCompleted = deal.getStatus() == DealStatus.ACTIVE
                ? deal.getDueDate() : deal.getCompletedDate();
        String text = dealNotificationText(deal.getId(), deal.getDealType(), deal.getStatus(),
                deal.getListing().getTitle(), deal.getBuyerDisplayName(), deal.getStartDate(),
                dueOrCompleted);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Deal details");
        alert.setHeaderText(deal.getListing().getTitle());
        alert.getDialogPane().setContent(notificationBody(text));
        alert.showAndWait();
    }

    /** Read-only details of the review a notification points at (SRS UC-2.7 step 3.2.1). */
    private void showReviewNotification(Review review) {
        String buyerLabel = DummyDataService.getInstance().users()
                .historicalLabel(review.getBuyerUsername());
        String text = reviewNotificationText(review.getRating(), review.getComment(), buyerLabel,
                review.getCreatedAt(), review.getSellerReply());
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Review details");
        alert.setHeaderText(review.getListingTitle() == null ? "Review" : review.getListingTitle());
        alert.getDialogPane().setContent(notificationBody(text));
        alert.showAndWait();
    }

    private static VBox notificationBody(String text) {
        Label body = new Label(text);
        body.setWrapText(true);
        VBox box = new VBox(body);
        box.setPadding(new Insets(4));
        box.setPrefWidth(360);
        return box;
    }

    /** The terms a request would grant: the counter if there is one, otherwise what was asked. */
    private static String requestTermsText(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return "HK$ " + offer.agreedPrice().toPlainString();
        }
        if (request instanceof BorrowRequest borrow) {
            return whenText(borrow.agreedStart()) + " to " + whenText(borrow.agreedEnd());
        }
        return "Giveaway claim";
    }

    /**
     * The facts shown when a REQUEST notification is opened: the request id, type, status,
     * listing, buyer, terms and when it was submitted. Takes primitives so the text is testable
     * without constructing a package-private {@link DealRequest}.
     */
    static String requestNotificationText(String requestId, DealType type, RequestStatus status,
                                          String listingTitle, String buyer, String terms,
                                          LocalDateTime submitted) {
        return "Request " + orDash(requestId) + "\n"
                + "Type: " + dealTypeLabel(type) + "\n"
                + "Status: " + requestStatusLabel(status) + "\n"
                + "Listing: " + orDash(listingTitle) + "\n"
                + "Buyer: " + orDash(buyer) + "\n"
                + "Terms: " + orDash(terms) + "\n"
                + "Submitted: " + whenText(submitted);
    }

    /**
     * The facts shown when a DEAL notification is opened: the deal id, type, status, listing,
     * buyer, start and its due (while active) or completed date. Takes primitives so the text is
     * testable without constructing a package-private {@link Deal}.
     */
    static String dealNotificationText(String dealId, DealType type, DealStatus status, String listingTitle,
                                       String buyer, LocalDateTime start, LocalDateTime dueOrCompleted) {
        String dateLabel = status == DealStatus.ACTIVE ? "Due" : "Completed";
        return "Deal " + orDash(dealId) + "\n"
                + "Type: " + dealTypeLabel(type) + "\n"
                + "Status: " + dealStatusLabel(status) + "\n"
                + "Listing: " + orDash(listingTitle) + "\n"
                + "Buyer: " + orDash(buyer) + "\n"
                + "Started: " + whenText(start) + "\n"
                + dateLabel + ": " + whenText(dueOrCompleted);
    }

    /**
     * The facts shown when a REVIEW notification is opened: the stars and numeric rating, the
     * comment, who wrote it, when, and the seller's reply when there is one. Takes primitives so
     * the text is testable without the review store.
     */
    static String reviewNotificationText(int rating, String comment, String buyerLabel,
                                         LocalDateTime created, String sellerReply) {
        String text = "Rating: " + stars(rating) + " (" + rating + "/5)\n"
                + "Comment: " + (comment == null || comment.isBlank() ? "No comment." : comment) + "\n"
                + "Buyer: " + orDash(buyerLabel) + "\n"
                + "Date: " + whenText(created);
        if (sellerReply != null && !sellerReply.isBlank()) {
            text += "\nSeller reply: " + sellerReply;
        }
        return text;
    }

    /**
     * The listing's deal outcome as shown on the stats drill-down (SRS UC-2.8 step 5).
     *
     * @param type the completed deal's type, or {@code null} when the listing has no completed deal
     * @return "Sold", "Lent" or "Given", or an em dash when there is no completed deal
     */
    static String outcomeLabel(DealType type) {
        if (type == null) {
            return "\u2014";
        }
        return switch (type) {
            case SOLD -> "Sold";
            case LENT -> "Lent";
            case GIVEN -> "Given";
        };
    }

    private static String dealTypeLabel(DealType type) {
        return switch (type) {
            case SOLD -> "Buy";
            case LENT -> "Borrow";
            case GIVEN -> "Claim";
        };
    }

    private static String requestStatusLabel(RequestStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case COUNTERED -> "Countered";
            case ACCEPTED -> "Accepted";
            case DECLINED -> "Declined";
        };
    }

    private static String dealStatusLabel(DealStatus status) {
        return switch (status) {
            case ACTIVE -> "Active";
            case RETURNED -> "Returned";
            case AUTO_RETURNED -> "Auto-returned";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String stars(int rating) {
        return "\u2605".repeat(rating) + "\u2606".repeat(5 - rating);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private static String whenText(LocalDateTime dateTime) {
        return dateTime == null ? "\u2014" : dateTime.format(REVIEW_DATE_FORMAT);
    }

    /** Manage Profile (SRS UC-2.6): name, bio, preferred meet-ups and an optional password change. */
    @FXML
    private void showProfile() {
        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Seller seller)) {
            new Alert(Alert.AlertType.WARNING,
                    "You must be logged in as a seller to manage your profile.").showAndWait();
            showMyListings();
            return;
        }

        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();

        VBox form = new VBox(10);
        form.setPadding(new Insets(8));
        form.setMaxWidth(560);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.getStyleClass().add("card");

        Label title = new Label("Edit your profile");
        title.getStyleClass().add("section-title");

        TextField nameField = new TextField(seller.getFullName());
        nameField.setPromptText("Full Name");
        nameField.setMaxWidth(Double.MAX_VALUE);

        TextArea bio = new TextArea(seller.getBio() == null ? "" : seller.getBio());
        bio.setWrapText(true);
        bio.setPrefRowCount(3);
        bio.setMaxWidth(Double.MAX_VALUE);
        Label counter = new Label(bio.getText().length() + "/500");
        counter.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        counter.setMaxWidth(Double.MAX_VALUE);
        counter.setAlignment(Pos.CENTER_RIGHT);
        bio.textProperty().addListener((obs, oldText, text) -> counter.setText(text.length() + "/500"));

        GridPane meetupGrid = new GridPane();
        meetupGrid.setHgap(16);
        meetupGrid.setVgap(8);
        EnumMap<Location, CheckBox> boxes = new EnumMap<>(Location.class);
        int i = 0;
        for (Location location : Location.values()) {
            CheckBox box = new CheckBox(location.getLabel());
            box.setSelected(seller.getPreferredMeetup().contains(location));
            meetupGrid.add(box, i % 2, i / 2);
            boxes.put(location, box);
            i++;
        }
        ScrollPane meetupScroll = new ScrollPane(meetupGrid);
        meetupScroll.setFitToWidth(true);
        meetupScroll.setPrefViewportHeight(160);
        Button clearAll = new Button("Clear all");
        clearAll.getStyleClass().addAll("btn-outline", "btn-sm");
        clearAll.setOnAction(e -> boxes.values().forEach(box -> box.setSelected(false)));

        Label passwordTitle = new Label("Change password (optional)");
        passwordTitle.getStyleClass().add("section-title");
        PasswordField currentPass = new PasswordField();
        currentPass.setPromptText("Current password");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New password");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new password");
        HBox currentPassRow = new HBox(6, currentPass);
        PasswordVisibility.attach(currentPass, currentPassRow);
        HBox newPassRow = new HBox(6, newPass);
        PasswordVisibility.attach(newPass, newPassRow);
        HBox confirmPassRow = new HBox(6, confirmPass);
        PasswordVisibility.attach(confirmPass, confirmPassRow);
        Label rules = new Label("8\u201364 characters with an uppercase letter, a lowercase letter, a digit and a"
                + " special character; no spaces; not your username.");
        rules.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        rules.setWrapText(true);

        Label message = new Label();
        message.setWrapText(true);
        message.setManaged(false);
        message.setVisible(false);

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            EnumSet<Location> chosen = EnumSet.noneOf(Location.class);
            for (Map.Entry<Location, CheckBox> entry : boxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    chosen.add(entry.getKey());
                }
            }
            try {
                List<String> changed = new ProfileService(DummyDataService.getInstance()).updateSellerProfile(
                        seller, nameField.getText(), bio.getText(), chosen,
                        currentPass.getText(), newPass.getText(), confirmPass.getText());
                currentPass.clear();
                newPass.clear();
                confirmPass.clear();
                if (changed.contains("full name")) {
                    welcomeLabel.setText("Hello, " + seller.getFullName());
                }
                showMessage(message, changed.isEmpty() ? "Nothing to save." : "Profile updated.", false);
            } catch (SecurityException | IllegalArgumentException ex) {
                // SRS UC-2.6 A1 / A2: say which check failed; nothing was changed.
                showMessage(message, ex.getMessage(), true);
            } catch (RuntimeException ex) {
                // The save itself failed and was rolled back.
                showMessage(message, "Your profile could not be saved. Please try again.", true);
            }
        });
        // SRS UC-2.6 A3: discard the edits.
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> showProfile());

        form.getChildren().addAll(title,
                new Label("Full Name"), nameField,
                new Label("Bio"), bio, counter,
                new Label("Preferred Meet-up Locations"), meetupScroll, clearAll,
                passwordTitle,
                new Label("Current password"), currentPassRow,
                new Label("New password"), newPassRow,
                new Label("Confirm new password"), confirmPassRow, rules,
                message, new HBox(10, saveBtn, cancelBtn));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private static void showMessage(Label label, String text, boolean error) {
        label.setText(text);
        label.setStyle(error ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1e7e34;");
        label.setManaged(true);
        label.setVisible(true);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
