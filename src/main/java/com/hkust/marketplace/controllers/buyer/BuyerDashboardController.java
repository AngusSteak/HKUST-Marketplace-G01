package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DealStore;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DealRequestService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.ListingSearch;
import com.hkust.marketplace.utils.ProfileService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnFavourites, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
        currentActiveBtn = btnBrowse;
        showBrowse();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    /** One choice in a filter box; a {@code null} value means "any". */
    private record Option<T>(String label, T value) {
        @Override
        public String toString() { return label; }
    }

    private static <T> ComboBox<Option<T>> optionBox(String anyLabel, List<T> values,
                                                     Function<T, String> label) {
        ComboBox<Option<T>> box = new ComboBox<>();
        box.getItems().add(new Option<>(anyLabel, null));
        values.forEach(v -> box.getItems().add(new Option<>(label.apply(v), v)));
        box.getSelectionModel().selectFirst();
        return box;
    }

    private static <T> T selected(ComboBox<Option<T>> box) {
        Option<T> option = box.getValue();
        return option == null ? null : option.value();
    }

    /** Browse Marketplace (SRS UC-1.3): search, filter and sort the live listings. */
    @FXML
    private void showBrowse() {
        setActive(btnBrowse, "Browse Marketplace");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        TextField searchField = new TextField();
        searchField.setPromptText("Search title or description...");
        searchField.setPrefWidth(320);
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        HBox searchBar = new HBox(10, searchField, searchBtn, clearBtn);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Option<Category>> categoryFilter =
                optionBox("All categories", Arrays.asList(Category.values()), Category::getLabel);
        ComboBox<Option<ListingType>> typeFilter =
                optionBox("All types", Arrays.asList(ListingType.values()), BuyerDashboardController::typeLabel);
        ComboBox<Option<Condition>> conditionFilter =
                optionBox("Any condition", Arrays.asList(Condition.values()), Condition::getLabel);
        TextField minPrice = priceField("Min HK$");
        TextField maxPrice = priceField("Max HK$");
        ComboBox<ListingSearch.SortOrder> sortBox =
                new ComboBox<>(FXCollections.observableArrayList(ListingSearch.SortOrder.values()));
        sortBox.getSelectionModel().select(ListingSearch.SortOrder.NEWEST);
        HBox filterBar = new HBox(10,
                categoryFilter, typeFilter, conditionFilter,
                new Label("Price:"), minPrice, new Label("\u2013"), maxPrice,
                new Label("Sort:"), sortBox);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px;");
        error.setManaged(false);
        error.setVisible(false);
        Label resultCount = new Label();
        resultCount.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        VBox results = new VBox(12);

        Runnable refresh = () -> {
            ListingSearch.Criteria criteria;
            List<Listing> found;
            try {
                criteria = new ListingSearch.Criteria(searchField.getText(),
                        selected(categoryFilter), selected(typeFilter), selected(conditionFilter),
                        ListingSearch.parsePrice(minPrice.getText()), ListingSearch.parsePrice(maxPrice.getText()), sortBox.getValue());
                found = ListingSearch.search(DummyDataService.getInstance().listings().all(), criteria);
            } catch (IllegalArgumentException ex) {
                // SRS UC-1.3 A2: say why and keep the current results.
                error.setText(ex.getMessage());
                error.setManaged(true);
                error.setVisible(true);
                return;
            }
            error.setManaged(false);
            error.setVisible(false);
            results.getChildren().clear();
            if (found.isEmpty()) {
                // SRS UC-1.3 A1
                resultCount.setText("0 listings");
                Label empty = new Label("No listings found.");
                empty.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 15px;");
                results.getChildren().add(empty);
                return;
            }
            resultCount.setText(found.size() + (found.size() == 1 ? " listing" : " listings"));
            FlowPane grid = new FlowPane(16, 16);
            grid.setPrefWrapLength(900);
            found.forEach(listing -> grid.getChildren().add(createListingCard(listing)));
            results.getChildren().add(grid);
        };

        searchBtn.setOnAction(e -> refresh.run());
        searchField.setOnAction(e -> refresh.run());
        minPrice.setOnAction(e -> refresh.run());
        maxPrice.setOnAction(e -> refresh.run());
        categoryFilter.setOnAction(e -> refresh.run());
        typeFilter.setOnAction(e -> refresh.run());
        conditionFilter.setOnAction(e -> refresh.run());
        sortBox.setOnAction(e -> refresh.run());
        clearBtn.setOnAction(e -> showBrowse());

        root.getChildren().addAll(searchBar, filterBar, error, resultCount, results);
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private static TextField priceField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(90);
        return field;
    }

    private static String typeLabel(ListingType type) {
        return switch (type) {
            case SELL -> "For Sale";
            case LEND -> "For Lending";
            case GIVEAWAY -> "Giveaway";
        };
    }

    /**
     * One listing in the grid, with the fields SRS UC-1.3 step 2 lists: thumbnail, title, seller
     * name, category, type, price, condition, meet-up location, posted date and availability.
     */
    private VBox createListingCard(Listing listing) {
        VBox card = new VBox(8);
        card.getStyleClass().add("listing-card");
        card.setPrefWidth(260);
        card.setMinHeight(200);

        // Type badge + price
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().add("badge");
        if (listing.getType() == ListingType.SELL) typeBadge.getStyleClass().add("badge-sold");
        else if (listing.getType() == ListingType.LEND) typeBadge.getStyleClass().add("badge-pending");
        else typeBadge.getStyleClass().add("badge-live");
        // Browse only lists Live listings, so every card is available.
        Label status = new Label("Available");
        status.getStyleClass().addAll("badge", "badge-live");

        top.getChildren().addAll(typeBadge, status);

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1a1a2e;");
        title.setWrapText(true);

        // Lend listings have no price; show how long they can be borrowed instead.
        Label price = new Label(priceText(listing));
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 16px;");
        price.setWrapText(true);

        Label meta = new Label(listing.getCategory().getLabel() + " · " + listing.getCondition().getLabel());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label seller = new Label("Seller: " + listing.getSellerFullName());
        seller.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label location = new Label("📍 " + listing.getMeetupLocation().getLabel());
        location.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label date = new Label("Posted: " + ListingSearch.postedDate(listing)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        date.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Button viewBtn = new Button("View Details");
        viewBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        viewBtn.setOnAction(e -> showListingDetails(listing));
        title.setOnMouseClicked(e -> showListingDetails(listing));
        title.setStyle(title.getStyle() + " -fx-cursor: hand;");

        // Deal requests start from the listing detail screen (SRS UC-1.3 special requirements).
        HBox actionRow = new HBox(8, viewBtn);

        card.getChildren().addAll(photo(firstPhoto(listing), 228, 120), top, title, price, meta, seller,
                location, date, actionRow);
        return card;
    }

    /** The type-specific deal button: Make Offer, Request Borrow or Claim Item. */
    private Button actionButton(Listing listing) {
        Button actionBtn = new Button();
        actionBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        switch (listing.getType()) {
            case SELL -> {
                actionBtn.setText("Make Offer");
                actionBtn.setOnAction(e -> showOfferDialog(listing));
            }
            case LEND -> {
                actionBtn.setText("Request Borrow");
                actionBtn.setOnAction(e -> showBorrowDialog(listing));
            }
            case GIVEAWAY -> {
                actionBtn.setText("Claim Item");
                actionBtn.setOnAction(e -> claimItem(listing));
            }
        }
        return actionBtn;
    }

    /** What a listing costs: the price, "Free", or how long a lend listing can be borrowed. */
    private static String priceText(Listing listing) {
        return listing instanceof LendListing lend
                ? "Borrow for up to " + DurationText.format(lend.getLendingDuration())
                : listing.getPriceDisplay();
    }

    private static String firstPhoto(Listing listing) {
        return listing.getPhotoPaths().isEmpty() ? null : listing.getPhotoPaths().get(0);
    }

    /**
     * A photo, or a placeholder when there is none or the file is missing or unreadable. Photos
     * load in the background so the screen never waits on them; "Loading photo..." shows
     * underneath until the image appears, and turns into "No photo" if loading fails.
     */
    private static StackPane photo(String path, double width, double height) {
        StackPane box = new StackPane();
        box.setPrefSize(width, height);
        box.setMinSize(width, height);
        box.setMaxSize(width, height);
        box.setStyle("-fx-background-color: #eef2f7; -fx-background-radius: 8;");
        Label placeholder = new Label("No photo");
        placeholder.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        box.getChildren().add(placeholder);
        Path file = path == null ? null : Path.of(path);
        if (file == null || !Files.isRegularFile(file)) {
            return box;
        }
        Image image = new Image(file.toUri().toString(), width, height, true, true, true);
        ImageView view = new ImageView(image);
        Runnable fallBack = () -> {
            box.getChildren().remove(view);
            placeholder.setText("No photo");
        };
        if (image.isError()) {
            return box;
        }
        placeholder.setText("Loading photo...");
        box.getChildren().add(view);
        image.errorProperty().addListener((obs, was, failed) -> {
            if (failed) {
                fallBack.run();
            }
        });
        return box;
    }

    // ---------- Listing details (SRS UC-1.3 S1) ----------

    /**
     * Shows one listing in full and counts the view. A listing that is no longer live (or whose
     * seller is disabled) is refused with a message instead (A3).
     */
    private void showListingDetails(Listing listing) {
        if (!ListingSearch.isBrowsable(listing)) {
            new Alert(Alert.AlertType.INFORMATION, "This listing is no longer available.").showAndWait();
            return;
        }
        DummyDataService data = DummyDataService.getInstance();
        try {
            data.listings().recordView(listing);
        } catch (IllegalArgumentException e) {
            // Deleted between the check above and now: same message as A3.
            new Alert(Alert.AlertType.INFORMATION, "This listing is no longer available.").showAndWait();
            return;
        }
        setActive(btnBrowse, "Listing Details");
        contentPane.getChildren().clear();

        Button back = new Button("\u2190 Back to Browse Marketplace");
        back.getStyleClass().addAll("btn-outline", "btn-sm");
        back.setOnAction(e -> showBrowse());

        FlowPane photos = new FlowPane(10, 10);
        List<String> paths = listing.getPhotoPaths();
        if (paths.isEmpty()) {
            photos.getChildren().add(photo(null, 260, 180));
        } else {
            paths.forEach(path -> photos.getChildren().add(photo(path, 260, 180)));
        }

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", listing.getType() == ListingType.SELL ? "badge-sold"
                : listing.getType() == ListingType.LEND ? "badge-pending" : "badge-live");
        Label status = new Label("Available");
        status.getStyleClass().addAll("badge", "badge-live");
        HBox badges = new HBox(6, typeBadge, status);

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 22px; -fx-text-fill: #1a1a2e;");
        title.setWrapText(true);
        Label price = new Label(priceText(listing));
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 18px;");

        Label description = new Label(listing.getDescription() == null || listing.getDescription().isBlank()
                ? "No description." : listing.getDescription());
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 14px;");

        GridPane fields = new GridPane();
        fields.setHgap(16);
        fields.setVgap(6);
        String[][] rows = {
                {"Category", listing.getCategory().getLabel()},
                {"Condition", listing.getCondition().getLabel()},
                {"Listing type", listing.getTypeDisplay()},
                {"Meet-up location", listing.getMeetupLocation().getLabel()},
                {"Posted", ListingSearch.postedDate(listing).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))},
                {"Views", String.valueOf(listing.getViewCount())},
                {"Seller", listing.getSellerFullName() + "  \u00b7  " + ratingText(listing.getSellerUsername())},
        };
        for (int i = 0; i < rows.length; i++) {
            Label name = new Label(rows[i][0]);
            name.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            Label value = new Label(rows[i][1]);
            value.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 13px;");
            fields.addRow(i, name, value);
        }

        HBox actions = new HBox(8, actionButton(listing));
        Button chatBtn = new Button("Chat");
        chatBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        chatBtn.setOnAction(e -> openChat(listing));
        Button reportBtn = new Button("Report Listing");
        reportBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        // TODO(#23): open the Report Listing form (SRS UC-1.10).
        reportBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Reporting listings is coming soon.").showAndWait());
        actions.getChildren().addAll(chatBtn, reportBtn);
        if (SessionManager.getCurrentUser() instanceof Buyer buyer) {
            // A plain variable, not a pattern: getSeller() already returns a Seller, which the
            // Eclipse/VS Code compiler rejects in an instanceof pattern.
            com.hkust.marketplace.models.Seller seller = listing.getSeller();
            Button favBtn = new Button();
            favBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            Runnable showFav = () -> favBtn.setText(data.favourites().isFavourite(buyer, listing)
                    ? "\u2665 Favourited" : "\u2661 Favourite");
            showFav.run();
            favBtn.setOnAction(e -> {
                data.favourites().toggleFavourite(buyer, listing);
                showFav.run();
            });
            Button followBtn = new Button();
            followBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            Runnable showFollow = () -> followBtn.setText(data.favourites().isFollowing(buyer, seller)
                    ? "\u2713 Following " + seller.getDisplayName() : "Follow Seller");
            showFollow.run();
            followBtn.setOnAction(e -> {
                data.favourites().toggleFollow(buyer, seller);
                showFollow.run();
            });
            actions.getChildren().addAll(favBtn, followBtn);
        }

        VBox card = new VBox(12, photos, badges, title, price, description, fields, actions);
        card.getStyleClass().add("listing-card");
        VBox root = new VBox(12, back, card);
        root.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /** "★ 4.5 (2 reviews)" from the seller's visible reviews, or "No ratings yet". */
    private static String ratingText(String sellerUsername) {
        DummyDataService data = DummyDataService.getInstance();
        java.util.OptionalDouble average = data.reviews().averageRating(sellerUsername);
        if (average.isEmpty()) {
            return "No ratings yet";
        }
        int count = data.reviews().visibleCount(sellerUsername);
        return String.format("\u2605 %.1f (%d review%s)", average.getAsDouble(), count, count == 1 ? "" : "s");
    }

    private void openChat(Listing listing) {
        if (!(SessionManager.getCurrentUser() instanceof Buyer buyer)) {
            new Alert(Alert.AlertType.WARNING, "You must be logged in as a buyer to chat.").showAndWait();
            return;
        }
        ChatThread thread = chatService.openThread(buyer, listing);
        pageTitle.setText("Chat: " + listing.getTitle());
        contentPane.getChildren().clear();

        Button back = new Button("\u2190 Back to Browse Marketplace");
        back.getStyleClass().addAll("btn-outline", "btn-sm");
        back.setOnAction(e -> showBrowse());

        VBox root = new VBox(12);
        root.getChildren().addAll(back, ChatViewBuilder.build(chatService, buyer, thread));
        contentPane.getChildren().add(root);
    }

    // ---------- Make Deal Request (SRS UC-1.4) ----------

    private final DealRequestService dealRequests = new DealRequestService(DummyDataService.getInstance());

    /** S1: price offer. The dialog stays open with the reason until the offer is valid (A2) or cancelled (A5). */
    private void showOfferDialog(Listing listing) {
        if (!(listing instanceof SaleListing sale)) {
            return;
        }
        TextField priceField = new TextField(sale.getPrice().amount().toPlainString());
        priceField.setPromptText("e.g. 150 or 150.50");
        Label error = dialogError();
        VBox body = new VBox(8,
                muted("Asking price: " + sale.getPriceDisplay()),
                new Label("Your offer (HK$)"), priceField, error);

        Dialog<ButtonType> dialog = requestDialog("Make an Offer", "Offer for: " + listing.getTitle(), body, "Send Offer");
        onConfirm(dialog, error, () -> {
            BigDecimal price = ListingSearch.parsePrice(priceField.getText());
            if (price == null || price.signum() <= 0) {
                throw new IllegalArgumentException("Please enter an offer above HK$0.");
            }
            return dealRequests.submitOffer(SessionManager.getCurrentUser(), listing, price);
        });
        dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(b -> requestSent("Your offer was sent to " + listing.getSellerFullName() + "."));
    }

    /** S2: borrow request with a start and end date and time, checked against the listing's limit (A3). */
    private void showBorrowDialog(Listing listing) {
        if (!(listing instanceof LendListing lend)) {
            return;
        }
        // The next full hour at least an hour away, so the default never slips into the past.
        LocalDateTime defaultStart = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        java.time.Duration longest = lend.getLendingDuration();
        java.time.Duration suggested = longest.compareTo(java.time.Duration.ofDays(7)) < 0 ? longest
                : java.time.Duration.ofDays(7);
        LocalDateTime defaultEnd = defaultStart.plus(suggested);
        DatePicker startDate = new DatePicker(defaultStart.toLocalDate());
        TextField startTime = timeField(defaultStart);
        DatePicker endDate = new DatePicker(defaultEnd.toLocalDate());
        TextField endTime = timeField(defaultEnd);
        Label error = dialogError();
        VBox body = new VBox(8,
                muted("You can borrow this for up to " + DurationText.format(longest) + "."),
                new Label("Start"), new HBox(8, startDate, startTime),
                new Label("End"), new HBox(8, endDate, endTime), error);

        Dialog<ButtonType> dialog = requestDialog("Request to Borrow", "Borrow: " + listing.getTitle(), body,
                "Send Request");
        onConfirm(dialog, error, () -> dealRequests.submitBorrow(SessionManager.getCurrentUser(), listing,
                dateTime(startDate, startTime, "start"), dateTime(endDate, endTime, "end")));
        dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(b -> requestSent("Your borrow request was sent to " + listing.getSellerFullName() + "."));
    }

    /** S3: claim with one confirmation; only the first claim wins (A4). */
    private void claimItem(Listing listing) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Claim '" + listing.getTitle() + "'? The item is reserved for you while "
                        + listing.getSellerFullName() + " responds.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Claim Item");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return; // A5
        }
        try {
            dealRequests.claim(SessionManager.getCurrentUser(), listing);
            requestSent("You claimed '" + listing.getTitle() + "'.");
        } catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
            new Alert(Alert.AlertType.INFORMATION, e.getMessage()).showAndWait();
            showBrowse();
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, "Your claim could not be completed."
                    + " Please check My Deals before trying again.").showAndWait();
            showMyDeals();
        }
    }

    private static Dialog<ButtonType> requestDialog(String title, String header, VBox body, String confirmText) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        body.setPrefWidth(380);
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        return dialog;
    }

    /**
     * Submits when the buyer confirms. An invalid entry keeps the dialog open with the reason;
     * if the listing has meanwhile become unavailable the dialog closes and says so.
     */
    private void onConfirm(Dialog<ButtonType> dialog, Label error, Supplier<DealRequest> submit) {
        Button confirm = (Button) dialog.getDialogPane().getButtonTypes().stream()
                .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .map(b -> dialog.getDialogPane().lookupButton(b)).findFirst().orElseThrow();
        confirm.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                submit.get();
            } catch (IllegalArgumentException e) {
                showMessage(error, e.getMessage(), true);
                event.consume();
            } catch (SecurityException | IllegalStateException e) {
                event.consume();
                dialog.close();
                new Alert(Alert.AlertType.INFORMATION, e.getMessage()).showAndWait();
                showBrowse();
            } catch (RuntimeException e) {
                // A database failure: the request may or may not have been saved.
                event.consume();
                dialog.close();
                new Alert(Alert.AlertType.ERROR, "Your request could not be completed."
                        + " Please check My Deals before trying again.").showAndWait();
                showMyDeals();
            }
        });
    }

    /** Step 7: confirm, and show the new Pending request in My Deals. */
    private void requestSent(String message) {
        new Alert(Alert.AlertType.INFORMATION, message + "\nYou can follow it in My Deals (status: Pending).")
                .showAndWait();
        showMyDeals();
    }

    /**
     * The red message line in a request dialog. Its two lines are reserved from the start, because
     * a dialog window does not grow when content appears, which would push the buttons out of view.
     */
    private static Label dialogError() {
        Label error = new Label();
        error.setWrapText(true);
        error.setMinHeight(36);
        return error;
    }

    private static TextField timeField(LocalDateTime value) {
        TextField field = new TextField(value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        field.setPromptText("HH:mm");
        field.setPrefWidth(80);
        return field;
    }

    private static LocalDateTime dateTime(DatePicker date, TextField time, String which) {
        if (date.getValue() == null) {
            throw new IllegalArgumentException("Please choose a " + which + " date.");
        }
        try {
            return date.getValue().atTime(java.time.LocalTime.parse(time.getText().trim(),
                    DateTimeFormatter.ofPattern("H:mm")));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Enter the " + which + " time as HH:mm, e.g. 14:30.");
        }
    }

    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        Label info = new Label("Active offers, borrow requests and claims");
        info.setStyle("-fx-text-fill: #5a5a7a;");
        root.getChildren().add(info);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<String[], String> colTitle = new TableColumn<>("Item");
        colTitle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        TableColumn<String[], String> colSeller = new TableColumn<>("Seller");
        colSeller.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        TableColumn<String[], String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));
        TableColumn<String[], String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[3]));
        TableColumn<String[], String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[4]));

        table.getColumns().addAll(colTitle, colSeller, colType, colStatus, colAction);

        String buyer = SessionManager.getCurrentUser().getUsername();
        DealStore deals = DummyDataService.getInstance().deals();
        List<String[]> rows = new ArrayList<>();
        for (DealRequest request : deals.openRequestsByBuyer(buyer)) {
            boolean countered = request.getStatus() == RequestStatus.COUNTERED;
            rows.add(new String[]{
                    request.getListing().getTitle(),
                    request.getListing().getSellerFullName(),
                    requestTypeLabel(request.dealType()),
                    (countered ? "Countered: " : "Pending: ") + requestTerms(request),
                    countered ? "Respond to the seller's counter" : "Waiting for the seller"});
        }
        for (Deal deal : deals.activeDealsForBuyer(buyer)) {
            rows.add(new String[]{
                    deal.getListing().getTitle(),
                    deal.getListing().getSellerFullName(),
                    requestTypeLabel(deal.getDealType()),
                    deal.getDueDate() != null
                            ? "Accepted, due " + deal.getDueDate().format(DEAL_TIME)
                            : "Accepted: " + requestTerms(deal.getRequest()),
                    deal.getDealType() == DealType.LENT ? "Return the item" : "Mark complete when received"});
        }
        table.setItems(FXCollections.observableArrayList(rows));
        table.setPlaceholder(new Label("You have no current offers, borrow requests or claims."));

        root.getChildren().add(table);
        Label note = new Label("💡 Responding to counters, returning and marking complete arrive with #21.");
        note.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        root.getChildren().add(note);

        contentPane.getChildren().add(root);
    }

    @FXML
    private void showHistory() {
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        TextField search = new TextField();
        search.setPromptText("Search by title / seller...");
        filters.getChildren().addAll(new Label("Date range:"), from, new Label("–"), to, search);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<String[], String> c1 = new TableColumn<>("Item Title");
        c1.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[0]));
        TableColumn<String[], String> c2 = new TableColumn<>("Seller");
        c2.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[1]));
        TableColumn<String[], String> c3 = new TableColumn<>("Deal Date");
        c3.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        TableColumn<String[], String> c4 = new TableColumn<>("Type");
        c4.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[3]));
        TableColumn<String[], String> c5 = new TableColumn<>("Outcome");
        c5.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[4]));
        table.getColumns().addAll(c1, c2, c3, c4, c5);
        String buyer = SessionManager.getCurrentUser().getUsername();
        List<String[]> rows = new ArrayList<>();
        for (Deal deal : DummyDataService.getInstance().deals().pastDealsForBuyer(buyer)) {
            rows.add(new String[]{
                    deal.getListing().getTitle(),
                    deal.getListing().getSellerFullName(),
                    deal.getCompletedDate().format(DEAL_TIME),
                    switch (deal.getDealType()) {
                        case SOLD -> "Bought";
                        case LENT -> "Borrowed";
                        case GIVEN -> "Received";
                    },
                    statusLabel(deal.getStatus())});
        }
        table.setItems(FXCollections.observableArrayList(rows));
        table.setPlaceholder(new Label("No past deals yet."));
        // TODO(#22): wire the date range and search filters.
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    private static final DateTimeFormatter DEAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static String requestTypeLabel(DealType type) {
        return switch (type) {
            case SOLD -> "Buy";
            case LENT -> "Borrow";
            case GIVEN -> "Claim";
        };
    }

    /** The terms the buyer would get: the counter if there is one, otherwise what they asked for. */
    private static String requestTerms(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return String.format("HK$ %s", offer.agreedPrice().toPlainString());
        }
        if (request instanceof BorrowRequest borrow) {
            return borrow.agreedStart().format(DEAL_TIME) + " to " + borrow.agreedEnd().format(DEAL_TIME);
        }
        return "free";
    }

    private static String statusLabel(DealStatus status) {
        return switch (status) {
            case ACTIVE -> "Active";
            case RETURNED -> "Returned";
            case AUTO_RETURNED -> "Auto-returned";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    /** The buyer's favourite listings and followed sellers (SRS UC-1.3 S1 steps 4-5). */
    @FXML
    private void showFavourites() {
        setActive(btnFavourites, "Favourites & Following");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        if (!(SessionManager.getCurrentUser() instanceof Buyer buyer)) {
            root.getChildren().add(new Label("Log in as a buyer to see your favourites."));
            contentPane.getChildren().add(root);
            return;
        }
        DummyDataService data = DummyDataService.getInstance();

        Label favTitle = new Label("Favourite listings");
        favTitle.getStyleClass().add("section-title");
        root.getChildren().add(favTitle);
        List<Listing> favourites = data.listings().findAll(data.favourites().favouriteListingIds(buyer));
        if (favourites.isEmpty()) {
            root.getChildren().add(muted("You haven't favourited any listings yet. Use \u2661 Favourite on a listing."));
        }
        for (Listing listing : favourites) {
            boolean available = ListingSearch.isBrowsable(listing);
            Label name = new Label(listing.getTitle());
            name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
            Label detail = muted(priceText(listing) + "  \u00b7  " + listing.getSellerFullName()
                    + "  \u00b7  " + (available ? "Available" : "No longer available"));
            VBox info = new VBox(2, name, detail);
            HBox.setHgrow(info, Priority.ALWAYS);
            Button view = new Button("View");
            view.getStyleClass().addAll("btn-primary", "btn-sm");
            view.setDisable(!available);
            view.setOnAction(e -> showListingDetails(listing));
            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("btn-outline", "btn-sm");
            remove.setOnAction(e -> {
                data.favourites().toggleFavourite(buyer, listing);
                showFavourites();
            });
            HBox row = new HBox(10, info, view, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("notif-item");
            root.getChildren().add(row);
        }

        Label followTitle = new Label("Followed sellers");
        followTitle.getStyleClass().add("section-title");
        root.getChildren().add(followTitle);
        List<String> followed = data.favourites().followedSellers(buyer);
        if (followed.isEmpty()) {
            root.getChildren().add(muted("You aren't following any sellers yet. Use Follow Seller on a listing."));
        }
        for (String username : followed) {
            if (!(data.users().find(username) instanceof com.hkust.marketplace.models.Seller seller)) {
                continue;
            }
            long live = data.listings().bySeller(username).stream().filter(ListingSearch::isBrowsable).count();
            Label name = new Label(seller.getDisplayName() + " (@" + seller.getUsername() + ")");
            name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
            Label detail = muted(ratingText(username) + "  \u00b7  " + live + " live listing" + (live == 1 ? "" : "s"));
            VBox info = new VBox(2, name, detail);
            HBox.setHgrow(info, Priority.ALWAYS);
            Button unfollow = new Button("Unfollow");
            unfollow.getStyleClass().addAll("btn-outline", "btn-sm");
            unfollow.setOnAction(e -> {
                data.favourites().toggleFollow(buyer, seller);
                showFavourites();
            });
            HBox row = new HBox(10, info, unfollow);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("notif-item");
            root.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        label.setWrapText(true);
        return label;
    }

    /** The categories a buyer receives (SRS UC-1.8 step 2), in the order of the filter box. */
    private static final List<NotificationCategory> BUYER_CATEGORIES = List.of(
            NotificationCategory.OFFER_UPDATE, NotificationCategory.CHAT_MESSAGE,
            NotificationCategory.BORROW_REMINDER, NotificationCategory.LISTING_REMOVED,
            NotificationCategory.REVIEW_REPLY, NotificationCategory.ANNOUNCEMENT);

    private NotificationCategory notificationFilter;

    /** Notification board (SRS UC-1.8): newest first, filter by category, open marks read. */
    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "";
        DummyDataService data = DummyDataService.getInstance();

        if (data.notifications().forUser(username).isEmpty()) {
            // SRS UC-1.8 A1
            root.getChildren().add(muted("You have no notifications."));
            contentPane.getChildren().add(root);
            return;
        }

        ComboBox<Option<NotificationCategory>> filter =
                optionBox("All categories", BUYER_CATEGORIES, NotificationCategory::getLabel);
        filter.getItems().stream().filter(o -> o.value() == notificationFilter).findFirst()
                .ifPresent(o -> filter.getSelectionModel().select(o));
        filter.setOnAction(e -> {
            notificationFilter = selected(filter);
            showNotifications();
        });
        long unread = data.notifications().unreadCount(username);
        Label unreadLabel = muted(unread == 0 ? "All read" : unread + " unread");
        HBox header = new HBox(12, new Label("Category:"), filter, unreadLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(header);

        List<Notification> notifs = data.notifications().forUser(username, notificationFilter);
        if (notifs.isEmpty()) {
            root.getChildren().add(muted("No notifications in this category."));
        }
        for (Notification n : notifs) {
            Label title = new Label(n.getTitle());
            title.setStyle("-fx-text-fill: #1a1a2e; " + (n.isRead() ? "" : "-fx-font-weight: bold;"));
            HBox titleRow = new HBox(8, title);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (!n.isRead()) {
                Label badge = new Label("New");
                badge.getStyleClass().addAll("badge", "badge-pending");
                titleRow.getChildren().add(badge);
            }
            Label msg = muted(n.getMessage());
            Label meta = new Label(n.getCategory().getLabel() + " \u00b7 "
                    + n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
            meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            VBox text = new VBox(4, titleRow, msg, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button open = new Button(n.getRelatedRef() == null ? "Mark read" : "Open");
            open.getStyleClass().addAll("btn-outline", "btn-sm");
            open.setDisable(n.getRelatedRef() == null && n.isRead());
            open.setOnAction(e -> openNotification(n));
            HBox item = new HBox(12, text, open);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("notif-item");
            root.getChildren().add(item);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Opens a notification (SRS UC-1.8 step 3.2): marks it read, then shows the related listing,
     * deal, chat thread or review. If that item is gone, the buyer stays on the board.
     */
    private void openNotification(Notification n) {
        DummyDataService data = DummyDataService.getInstance();
        if (!n.isRead()) {
            data.notifications().markRead(n, true);
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
                } else {
                    showListingDetails(listing);
                }
            }
            case REQUEST, DEAL -> showMyDeals();
            case THREAD -> {
                // A thread is one buyer's chat on one listing; the id may be "buyer|L101" or just the listing id.
                String id = ref.getId();
                Listing listing = data.listings().find(id.substring(id.indexOf('|') + 1));
                if (listing == null) {
                    new Alert(Alert.AlertType.INFORMATION, "This chat's listing no longer exists.").showAndWait();
                } else {
                    openChat(listing);
                }
            }
            // TODO(#23): open the buyer's review once Manage Reviews exists.
            case REVIEW -> showHistory();
            case USER -> {
                // A buyer is only ever told about their own account.
                User me = SessionManager.getCurrentUser();
                if (me != null && me.getUsername().equalsIgnoreCase(ref.getId())) {
                    showProfile();
                }
            }
        }
    }

    /** Manage Profile (SRS UC-1.7): full name and an optional password change. */
    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        contentPane.getChildren().clear();

        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(10);
        form.setPadding(new Insets(8));
        form.setMaxWidth(440);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.getStyleClass().add("card");

        Label title = new Label("Edit your profile");
        title.getStyleClass().add("section-title");

        TextField nameField = new TextField(user != null ? user.getFullName() : "");
        nameField.setPromptText("Full Name");
        String hall = user instanceof Buyer buyer && buyer.getResidenceHall() != null
                ? buyer.getResidenceHall() : "Not set";
        Label residence = muted(hall);

        Label passwordTitle = new Label("Change password (optional)");
        passwordTitle.getStyleClass().add("section-title");
        PasswordField currentPass = new PasswordField();
        currentPass.setPromptText("Current password");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New password");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new password");
        Label rules = muted("8\u201364 characters with an uppercase letter, a lowercase letter, a digit and a"
                + " special character; no spaces; not your username.");

        Label message = new Label();
        message.setWrapText(true);
        message.setManaged(false);
        message.setVisible(false);

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            try {
                List<String> changed = new ProfileService(DummyDataService.getInstance()).updateProfile(
                        user, nameField.getText(), currentPass.getText(), newPass.getText(), confirmPass.getText());
                currentPass.clear();
                newPass.clear();
                confirmPass.clear();
                if (!changed.isEmpty()) {
                    welcomeLabel.setText("Hello, " + user.getFullName());
                }
                showMessage(message, changed.isEmpty() ? "Nothing to save." : "Profile updated.", false);
            } catch (SecurityException | IllegalArgumentException ex) {
                // SRS UC-1.7 A1 / A2: say which check failed; nothing was changed.
                showMessage(message, ex.getMessage(), true);
            } catch (RuntimeException ex) {
                // The save itself failed and was rolled back.
                showMessage(message, "Your profile could not be saved. Please try again.", true);
            }
        });
        // SRS UC-1.7 A3: discard the edits.
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> showProfile());

        form.getChildren().addAll(title,
                new Label("Full Name"), nameField,
                new Label("Residence hall"), residence,
                passwordTitle,
                new Label("Current password"), currentPass,
                new Label("New password"), newPass,
                new Label("Confirm new password"), confirmPass, rules,
                message, new HBox(10, saveBtn, cancelBtn));

        contentPane.getChildren().add(form);
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
