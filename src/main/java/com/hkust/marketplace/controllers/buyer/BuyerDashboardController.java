package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.controllers.chat.ChatViewBuilder;
import com.hkust.marketplace.controllers.common.DateTimeInput;
import com.hkust.marketplace.controllers.common.ListingDetailDialog;
import com.hkust.marketplace.controllers.common.ListingPhotos;
import com.hkust.marketplace.controllers.notifications.NotificationBoardView;
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
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationRef;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DealHistorySearch;
import com.hkust.marketplace.utils.DealStore;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.DealRequestService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.ListingSearch;
import com.hkust.marketplace.utils.MyDealsService;
import com.hkust.marketplace.utils.PasswordVisibility;
import com.hkust.marketplace.utils.ProfileService;
import com.hkust.marketplace.utils.ReviewService;
import com.hkust.marketplace.utils.SellerProfileService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Buyer dashboard controller: builds the browse, deals, history, favourites, notifications and
 * profile screens in one content pane. All writes go through the service classes.
 */
public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnChats, btnHistory, btnReviews, btnFavourites, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());

    /** Builds the dashboard after the FXML is loaded. */
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
        // Wraps onto a second line in a narrow window instead of squeezing the controls to "...".
        // The price range and the sort each stay together as one group.
        HBox priceGroup = filterGroup(new Label("Price:"), minPrice, new Label("\u2013"), maxPrice);
        HBox sortGroup = filterGroup(new Label("Sort:"), sortBox);
        FlowPane filterBar = new FlowPane(10, 8, categoryFilter, typeFilter, conditionFilter, priceGroup, sortGroup);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.prefWrapLengthProperty().bind(contentPane.widthProperty().subtract(40));
        for (javafx.scene.Node n : List.of(categoryFilter, typeFilter, conditionFilter, sortBox)) {
            ((Region) n).setMinWidth(Region.USE_PREF_SIZE);
        }

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

    /** Controls kept on one line at their natural width, so none of them is cut short. */
    private static HBox filterGroup(javafx.scene.Node... nodes) {
        HBox group = new HBox(8, nodes);
        group.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node n : nodes) {
            if (n instanceof Region r) {
                r.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
        return group;
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

    private static StackPane photo(String path, double width, double height) {
        return ListingPhotos.photo(path, width, height);
    }

    // ---------- Listing details (SRS UC-1.3 S1) ----------

    /**
     * Shows one listing in full and counts the view. A listing that is no longer live (or whose
     * seller is disabled) is refused with a message instead (A3).
     */
    private void showListingDetails(Listing listing) {
        showListingDetails(listing, false);
    }

    /**
     * Shows a listing in full. From browsing ({@code fromDeal} false) it must still be available
     * and the view is counted. From My Deals (SRS UC-1.6 step 3.1) it is shown whatever its status,
     * without the deal button once it is no longer available, and with the linked chat below.
     */
    private void showListingDetails(Listing listing, boolean fromDeal) {
        showListingDetails(listing, fromDeal, !fromDeal);
    }

    /** As above; {@code countView} is false when returning to the page, e.g. from the seller's profile. */
    private void showListingDetails(Listing listing, boolean fromDeal, boolean countView) {
        boolean available = ListingSearch.isBrowsable(listing);
        DummyDataService data = DummyDataService.getInstance();
        if (!fromDeal && !available) {
            new Alert(Alert.AlertType.INFORMATION, "This listing is no longer available.").showAndWait();
            return;
        }
        if (countView) {
            try {
                data.listings().recordView(listing);
            } catch (IllegalArgumentException e) {
                // Deleted between the check above and now: same message as A3.
                new Alert(Alert.AlertType.INFORMATION, "This listing is no longer available.").showAndWait();
                return;
            }
        }
        setActive(fromDeal ? btnDeals : btnBrowse, fromDeal ? "Deal: " + listing.getTitle() : "Listing Details");
        contentPane.getChildren().clear();

        Button back = new Button(fromDeal ? "\u2190 Back to My Deals" : "\u2190 Back to Browse Marketplace");
        back.getStyleClass().addAll("btn-outline", "btn-sm");
        back.setOnAction(e -> {
            if (fromDeal) {
                showMyDeals();
            } else {
                showBrowse();
            }
        });

        FlowPane photos = ListingPhotos.gallery(listing, 260, 180);

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", listing.getType() == ListingType.SELL ? "badge-sold"
                : listing.getType() == ListingType.LEND ? "badge-pending" : "badge-live");
        Label status = new Label(available ? "Available" : listingStatusLabel(listing.getStatus()));
        status.getStyleClass().addAll("badge", available ? "badge-live" : "badge-sold");
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
        Hyperlink sellerLink = new Hyperlink("View " + listing.getSellerFullName() + "'s profile and reviews");
        sellerLink.setOnAction(e -> showSellerProfile(listing.getSellerUsername(), "listing",
                () -> showListingDetails(listing, fromDeal, false)));
        fields.addRow(rows.length, new Label(""), sellerLink);

        HBox actions = new HBox(8);
        if (available) {
            actions.getChildren().add(actionButton(listing));
        }
        Button chatBtn = new Button("Chat");
        chatBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        chatBtn.setOnAction(e -> openChat(listing, "Browse Marketplace", this::showBrowse));
        Button reportBtn = new Button("Report Listing");
        reportBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        reportBtn.setOnAction(e -> showReportDialog(listing));
        if (!fromDeal) {
            // From My Deals the chat is shown below the listing instead.
            actions.getChildren().add(chatBtn);
        }
        actions.getChildren().add(reportBtn);
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
        card.getChildren().add(sellerReviews(listing.getSellerUsername(), listing.getSellerFullName()));
        card.getStyleClass().add("listing-card");
        VBox root = new VBox(12, back, card);
        if (fromDeal && SessionManager.getCurrentUser() instanceof Buyer buyer) {
            Label chatTitle = new Label("Chat with " + listing.getSellerFullName());
            chatTitle.getStyleClass().add("section-title");
            root.getChildren().addAll(chatTitle,
                    ChatViewBuilder.build(chatService, buyer, chatService.openThread(buyer, listing)));
        }
        root.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    // ---------- Seller profile (brief 1.10) ----------

    /**
     * A seller's public profile: details, average rating, every visible review with any seller
     * reply, and their live listings (each opens its details). {@code back} returns to where the
     * buyer came from.
     */
    private void showSellerProfile(String username, String backTo, Runnable back) {
        showSellerProfile(username, backTo, back, btnBrowse);
    }

    /** As above, keeping {@code section} highlighted in the sidebar (where the profile was opened). */
    private void showSellerProfile(String username, String backTo, Runnable back, Button section) {
        SellerProfileService.SellerProfile profile;
        try {
            profile = new SellerProfileService(DummyDataService.getInstance()).profile(username);
        } catch (IllegalArgumentException e) {
            new Alert(Alert.AlertType.INFORMATION, "This seller could not be found.").showAndWait();
            return;
        }
        com.hkust.marketplace.models.Seller seller = profile.seller();
        setActive(section, "Seller: " + seller.getDisplayName());
        contentPane.getChildren().clear();

        Button backBtn = new Button("\u2190 Back to " + backTo);
        backBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        backBtn.setOnAction(e -> back.run());

        Label name = new Label(seller.getDisplayName() + (profile.deleted() ? "" : "  (@" + seller.getUsername() + ")"));
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 22px; -fx-text-fill: #1a1a2e;");
        Label rating = new Label(profile.averageRating().isEmpty() ? "No ratings yet"
                : String.format("\u2605 %.1f (%d review%s)", profile.averageRating().getAsDouble(),
                        profile.reviews().size(), profile.reviews().size() == 1 ? "" : "s"));
        rating.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 16px;");
        VBox header = new VBox(6, name, rating);
        if (profile.deleted()) {
            header.getChildren().add(muted("This account has been deleted. Its past reviews are kept."));
        } else {
            if (seller.getBio() != null && !seller.getBio().isBlank()) {
                Label bio = new Label(seller.getBio());
                bio.setWrapText(true);
                header.getChildren().add(bio);
            }
            String meetup = seller.getPreferredMeetup().isEmpty() ? "Not set"
                    : seller.getPreferredMeetup().stream().map(l -> l.getLabel()).sorted()
                            .collect(java.util.stream.Collectors.joining(", "));
            header.getChildren().addAll(muted("Preferred meet-up: " + meetup),
                    muted("Member since " + seller.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"))));
        }
        header.getStyleClass().add("listing-card");

        VBox root = new VBox(12, backBtn, header, sectionTitle("Reviews (" + profile.reviews().size() + ")"));
        root.setPadding(new Insets(4));
        if (profile.reviews().isEmpty()) {
            root.getChildren().add(muted("No reviews yet."));
        }
        for (Review review : profile.reviews()) {
            root.getChildren().add(reviewCard(review, "by " + displayName(review.getBuyerUsername())));
        }

        if (!profile.deleted()) {
            root.getChildren().add(sectionTitle("Live listings (" + profile.liveListings().size() + ")"));
            if (profile.liveListings().isEmpty()) {
                root.getChildren().add(muted("No live listings right now."));
            } else {
                FlowPane grid = new FlowPane(16, 16);
                grid.setPrefWrapLength(900);
                profile.liveListings().forEach(l -> grid.getChildren().add(createListingCard(l)));
                root.getChildren().add(grid);
            }
        }

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

    /**
     * Chat About Listing (SRS UC-1.5) in the shared chat component: the one thread between this
     * buyer and the listing's seller, created on first use. {@code back} returns to where the
     * buyer came from.
     */
    private void openChat(Listing listing, String backTo, Runnable back) {
        if (!(SessionManager.getCurrentUser() instanceof Buyer buyer)) {
            new Alert(Alert.AlertType.WARNING, "You must be logged in as a buyer to chat.").showAndWait();
            return;
        }
        ChatThread thread = chatService.openThread(buyer, listing);
        pageTitle.setText("Chat: " + listing.getTitle());
        contentPane.getChildren().clear();

        Button backBtn = new Button("\u2190 Back to " + backTo);
        backBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        backBtn.setOnAction(e -> back.run());

        VBox root = new VBox(12);
        root.getChildren().addAll(backBtn, ChatViewBuilder.build(chatService, buyer, thread));
        contentPane.getChildren().add(root);
    }

    /**
     * The buyer's chat threads, most recent activity first, so an existing conversation can be
     * reopened (SRS UC-1.5 step 1), including threads on listings that are no longer live.
     */
    @FXML
    private void showChats() {
        setActive(btnChats, "Chats");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        if (!(SessionManager.getCurrentUser() instanceof Buyer buyer)) {
            root.getChildren().add(muted("Log in as a buyer to see your chats."));
            contentPane.getChildren().add(root);
            return;
        }
        List<ChatThread> threads = new ArrayList<>(chatService.threadsForBuyer(buyer));
        if (threads.isEmpty()) {
            root.getChildren().add(muted("You have no chats yet. Open a listing and choose Chat to message its seller."));
            contentPane.getChildren().add(root);
            return;
        }
        threads.sort(java.util.Comparator.comparing(this::lastActivity,
                java.util.Comparator.nullsLast(java.util.Comparator.<LocalDateTime>reverseOrder())));
        DateTimeFormatter when = DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (ChatThread thread : threads) {
            Listing listing = thread.getListing();
            List<Message> history = chatService.history(thread);
            Message last = history.isEmpty() ? null : history.get(history.size() - 1);
            Label title = new Label(listing.getTitle());
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
            Label status = new Label(chatService.acceptsOffers(thread) ? "Live" : "Offers closed");
            status.getStyleClass().addAll("badge", chatService.acceptsOffers(thread) ? "badge-live" : "badge-sold");
            HBox titleRow = new HBox(8, title, status);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            String preview = last == null ? "No messages yet"
                    : (last.getSenderUsername().equalsIgnoreCase(buyer.getUsername()) ? "You: " : "")
                    + last.getContent();
            Label detail = muted("with " + thread.getSeller().getNameWithUsername() + "  \u00b7  " + preview);
            detail.setMaxWidth(700);
            detail.setWrapText(false);
            Label time = new Label(last == null ? "" : last.getSentTime().format(when));
            time.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            VBox info = new VBox(3, titleRow, detail, time);
            HBox.setHgrow(info, Priority.ALWAYS);
            Button open = new Button("Open");
            open.getStyleClass().addAll("btn-primary", "btn-sm");
            open.setOnAction(e -> openChat(listing, "Chats", this::showChats));
            HBox row = new HBox(12, info, open);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("notif-item");
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /** When the thread last had a message, or {@code null} if it has none. */
    private LocalDateTime lastActivity(ChatThread thread) {
        List<Message> history = chatService.history(thread);
        return history.isEmpty() ? null : history.get(history.size() - 1).getSentTime();
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
        DateTimeInput start = new DateTimeInput(defaultStart);
        DateTimeInput end = new DateTimeInput(defaultEnd);
        Label error = dialogError();
        VBox body = new VBox(8,
                muted("You can borrow this for up to " + DurationText.format(longest) + "."),
                new Label("Start"), start.node(),
                new Label("End"), end.node(), error);

        Dialog<ButtonType> dialog = requestDialog("Request to Borrow", "Borrow: " + listing.getTitle(), body,
                "Send Request");
        onConfirm(dialog, error, () -> dealRequests.submitBorrow(SessionManager.getCurrentUser(), listing,
                start.value("start"), end.value("end")));
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
    private void onConfirm(Dialog<ButtonType> dialog, Label error, Supplier<?> submit) {
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

    /** Combines a date and an {@code H:mm} or {@code H:mm:ss} time; see {@link DateTimeInput#parse}. */
    static LocalDateTime borrowDateTime(LocalDate date, String timeText, String which) {
        return DateTimeInput.parse(date, timeText, which);
    }

    private final MyDealsService myDeals = new MyDealsService(DummyDataService.getInstance());

    /**
     * My Deals (SRS UC-1.6): the buyer's Pending, Countered and Accepted requests with what can be
     * done next: open the listing and chat, answer a counter, return a borrowed item or mark a
     * purchase or claim complete.
     */
    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        String buyer = SessionManager.getCurrentUser() == null ? "" : SessionManager.getCurrentUser().getUsername();
        DealStore deals = DummyDataService.getInstance().deals();
        List<DealRequest> open = deals.openRequestsByBuyer(buyer);
        List<Deal> active = deals.activeDealsForBuyer(buyer);
        List<Deal> returned = deals.returnedDealsForBuyer(buyer);

        if (open.isEmpty() && active.isEmpty() && returned.isEmpty()) {
            // SRS UC-1.6 A1: only View History remains.
            Button history = new Button("View Deal History");
            history.getStyleClass().addAll("btn-outline", "btn-sm");
            history.setOnAction(e -> showHistory());
            root.getChildren().addAll(muted("You have no current deals."), history);
            contentPane.getChildren().add(root);
            return;
        }

        if (!active.isEmpty()) {
            root.getChildren().add(sectionTitle("Accepted"));
            for (Deal deal : active) {
                root.getChildren().add(dealRow(deal));
            }
        }
        if (!open.isEmpty()) {
            root.getChildren().add(sectionTitle("Waiting for a response"));
            for (DealRequest request : open) {
                root.getChildren().add(requestRow(request));
            }
        }
        if (!returned.isEmpty()) {
            root.getChildren().add(sectionTitle("Recently returned"));
            for (Deal deal : returned) {
                root.getChildren().add(returnedRow(deal));
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox requestRow(DealRequest request) {
        boolean countered = request.getStatus() == RequestStatus.COUNTERED;
        Label status = new Label(countered ? "Countered" : "Pending");
        status.getStyleClass().addAll("badge", countered ? "badge-pending" : "badge-sold");
        VBox info = rowInfo(request.getListing(), requestTypeLabel(request.dealType()), status);
        if (countered) {
            info.getChildren().add(muted("You asked for " + askedTerms(request) + "  \u2192  the seller countered with "
                    + counterTerms(request)));
        } else {
            info.getChildren().add(muted("You asked for " + askedTerms(request) + ". Waiting for the seller."));
        }
        HBox buttons = new HBox(8, openButton(request.getListing()));
        if (countered) {
            Button accept = new Button("Accept Counter");
            accept.getStyleClass().addAll("btn-primary", "btn-sm");
            accept.setOnAction(e -> confirmThen("Accept the counter-offer of " + counterTerms(request) + " for '"
                    + request.getListing().getTitle() + "'?", () -> {
                myDeals.acceptCounter(SessionManager.getCurrentUser(), request);
                info("Counter-offer accepted. The deal for '" + request.getListing().getTitle() + "' is now active.");
            }));
            Button decline = new Button("Decline");
            decline.getStyleClass().addAll("btn-outline", "btn-sm");
            decline.setOnAction(e -> confirmThen("Decline the counter-offer? Your request will be closed.", () -> {
                myDeals.declineCounter(SessionManager.getCurrentUser(), request);
                info("Counter-offer declined.");
            }));
            buttons.getChildren().addAll(accept, decline);
        }
        return actionRow(info, buttons);
    }

    private HBox dealRow(Deal deal) {
        Label status = new Label("Accepted");
        status.getStyleClass().addAll("badge", "badge-live");
        VBox info = rowInfo(deal.getListing(), requestTypeLabel(deal.getDealType()), status);
        HBox buttons = new HBox(8, openButton(deal.getListing()));
        if (deal.getDealType() == DealType.LENT) {
            boolean overdue = !LocalDateTime.now().isBefore(deal.getDueDate());
            Label due = muted("Borrowed " + requestTerms(deal.getRequest()) + "  \u00b7  due "
                    + deal.getDueDate().format(DEAL_TIME) + (overdue ? " (overdue)" : ""));
            info.getChildren().add(due);
            Button returnBtn = new Button("Return Item");
            returnBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            returnBtn.setOnAction(e -> confirmThen("Confirm you have returned '" + deal.getListing().getTitle()
                    + "' to " + deal.getSellerDisplayName() + "?", () -> {
                myDeals.returnItem(SessionManager.getCurrentUser(), deal);
                info("Return recorded. '" + deal.getListing().getTitle() + "' is live again.");
            }));
            buttons.getChildren().add(returnBtn);
        } else {
            info.getChildren().add(muted((deal.getDealType() == DealType.SOLD ? "Price " : "")
                    + requestTerms(deal.getRequest()) + "  \u00b7  mark complete once you have the item"));
            Button complete = new Button("Mark Complete");
            complete.getStyleClass().addAll("btn-primary", "btn-sm");
            complete.setOnAction(e -> confirmThen("Confirm you have received '" + deal.getListing().getTitle() + "'?",
                    () -> {
                        myDeals.markComplete(SessionManager.getCurrentUser(), deal);
                        promptRating(deal);
                    }));
            buttons.getChildren().add(complete);
        }
        return actionRow(info, buttons);
    }

    /** A read-only recently returned loan: outcome, listing, seller and return date, no actions. */
    private HBox returnedRow(Deal deal) {
        Label status = new Label(statusLabel(deal.getStatus()));
        status.getStyleClass().addAll("badge", "badge-sold");
        VBox info = rowInfo(deal.getListing(), requestTypeLabel(deal.getDealType()), status);
        String date = deal.getCompletedDate() == null ? "" : deal.getCompletedDate().format(DEAL_TIME);
        info.getChildren().add(muted((deal.getStatus() == DealStatus.AUTO_RETURNED ? "Auto-returned " : "Returned ")
                + (date.isEmpty() ? "after the due date" : "on " + date)));
        return actionRow(info, new HBox(8, openButton(deal.getListing())));
    }

    /** SRS UC-1.6 step 3.4.4: after completing, the buyer is invited to rate the seller. */
    private void promptRating(Deal deal) {
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION, "Deal complete! Would you like to rate "
                + deal.getSellerDisplayName() + " now?", new ButtonType("Rate Seller", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE));
        ask.setHeaderText(null);
        ask.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(b -> showRateDialog(deal));
    }

    private VBox rowInfo(Listing listing, String type, Label status) {
        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        HBox titleRow = new HBox(8, title, status);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        return new VBox(3, titleRow, muted(type + "  \u00b7  " + listing.getSellerFullName()));
    }

    private Button openButton(Listing listing) {
        Button open = new Button("Open");
        open.getStyleClass().addAll("btn-outline", "btn-sm");
        open.setOnAction(e -> showListingDetails(listing, true));
        return open;
    }

    private static HBox actionRow(VBox info, HBox buttons) {
        HBox.setHgrow(info, Priority.ALWAYS);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(12, info, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("notif-item");
        return row;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    /**
     * Asks to confirm, runs the action, then refreshes My Deals. A refusal (e.g. the counter is no
     * longer valid, or the loan was already auto-returned) is shown and the list refreshed, since
     * the action may have changed the deal before refusing.
     */
    private void confirmThen(String question, Runnable action) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            action.run();
        } catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
            new Alert(Alert.AlertType.INFORMATION, e.getMessage()).showAndWait();
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, "That could not be saved. Please try again.").showAndWait();
        }
        showMyDeals();
    }

    private static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** What the buyer originally asked for. */
    private static String askedTerms(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return "HK$ " + offer.getOfferedPrice().toPlainString();
        }
        if (request instanceof BorrowRequest borrow) {
            return borrow.getStartDate().format(DEAL_TIME) + " to " + borrow.getEndDate().format(DEAL_TIME);
        }
        return "the item";
    }

    /** The seller's counter terms. */
    private static String counterTerms(DealRequest request) {
        if (request instanceof PriceOffer offer && offer.getCounterPrice() != null) {
            return "HK$ " + offer.getCounterPrice().toPlainString();
        }
        if (request instanceof BorrowRequest borrow && borrow.getCounterStart() != null) {
            return borrow.getCounterStart().format(DEAL_TIME) + " to " + borrow.getCounterEnd().format(DEAL_TIME);
        }
        return requestTerms(request);
    }

    private static String listingStatusLabel(com.hkust.marketplace.models.ListingStatus status) {
        String name = status.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Deal History (SRS UC-1.6 S1): finished deals, filtered by date range, category, seller and keyword. */
    @FXML
    private void showHistory() {
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        DatePicker from = new DatePicker();
        from.setPromptText("From");
        from.setPrefWidth(140);
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        to.setPrefWidth(140);
        ComboBox<Option<Category>> category =
                optionBox("All categories", Arrays.asList(Category.values()), Category::getLabel);
        TextField seller = new TextField();
        seller.setPromptText("Seller");
        seller.setPrefWidth(140);
        TextField keyword = new TextField();
        keyword.setPromptText("Keyword in title...");
        keyword.setPrefWidth(180);
        Button clear = new Button("Clear");
        clear.getStyleClass().addAll("btn-outline", "btn-sm");
        HBox filters = new HBox(10, new Label("From"), from, new Label("to"), to, category, seller, keyword, clear);
        filters.setAlignment(Pos.CENTER_LEFT);

        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px;");
        error.setManaged(false);
        error.setVisible(false);

        TableView<Deal> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(dealTitleColumn());
        table.getColumns().add(column("Seller", Deal::getSellerDisplayName));
        table.getColumns().add(column("Category", d -> d.getListing().getCategory().getLabel()));
        table.getColumns().add(column("Deal Date", d -> d.getCompletedDate().format(DEAL_TIME)));
        table.getColumns().add(column("Type", d -> switch (d.getDealType()) {
            case SOLD -> "Bought";
            case LENT -> "Borrowed";
            case GIVEN -> "Received";
        }));
        table.getColumns().add(column("Outcome", d -> statusLabel(d.getStatus())));
        table.getStyleClass().add("link-table");
        table.setFocusTraversable(false);

        String buyer = SessionManager.getCurrentUser() == null ? "" : SessionManager.getCurrentUser().getUsername();
        List<Deal> past = DummyDataService.getInstance().deals().pastDealsForBuyer(buyer);
        table.setPlaceholder(new Label(past.isEmpty() ? "No past deals yet." : "No deals match these filters."));

        Runnable refresh = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(DealHistorySearch.filter(past,
                        new DealHistorySearch.Criteria(from.getValue(), to.getValue(), selected(category),
                                seller.getText(), keyword.getText()))));
                error.setManaged(false);
                error.setVisible(false);
            } catch (IllegalArgumentException e) {
                // SRS UC-1.6 A4: say why and keep the current results.
                error.setText(e.getMessage());
                error.setManaged(true);
                error.setVisible(true);
            }
        };
        from.setOnAction(e -> refresh.run());
        to.setOnAction(e -> refresh.run());
        category.setOnAction(e -> refresh.run());
        seller.textProperty().addListener((o, a, b) -> refresh.run());
        keyword.textProperty().addListener((o, a, b) -> refresh.run());
        clear.setOnAction(e -> showHistory());
        refresh.run();

        root.getChildren().addAll(filters, error, muted("Click an item title to see the item."), table);
        contentPane.getChildren().add(root);
    }

    private static TableColumn<Deal, String> column(String title, Function<Deal, String> value) {
        TableColumn<Deal, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(value.apply(d.getValue())));
        return column;
    }

    private TableColumn<Deal, String> dealTitleColumn() {
        TableColumn<Deal, String> column = column("Item Title", d -> d.getListing().getTitle());
        column.setCellFactory(c -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();

            {
                link.getStyleClass().add("deal-link");
                link.setOnAction(e -> {
                    Deal deal = getTableRow() == null ? null : getTableRow().getItem();
                    if (deal != null) {
                        ListingDetailDialog.show(getScene() == null ? null : getScene().getWindow(),
                                deal.getListing());
                    }
                });
            }

            @Override
            protected void updateItem(String title, boolean empty) {
                super.updateItem(title, empty);
                if (empty || title == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    link.setText(title);
                    setGraphic(link);
                    setText(null);
                }
            }
        });
        return column;
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

    // ---------- Manage Reviews (SRS UC-1.9) and Report Listing (UC-1.10) ----------

    private final ReviewService reviewService = new ReviewService(DummyDataService.getInstance());

    /** Deals ready to review, then every review the buyer has written with any seller reply. */
    @FXML
    private void showReviews() {
        setActive(btnReviews, "My Reviews");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = SessionManager.getCurrentUser();
        if (!(user instanceof Buyer)) {
            root.getChildren().add(muted("Log in as a buyer to manage your reviews."));
            contentPane.getChildren().add(root);
            return;
        }

        root.getChildren().add(sectionTitle("Ready to review"));
        List<Deal> reviewable = reviewService.reviewableDeals(user);
        if (reviewable.isEmpty()) {
            root.getChildren().add(muted("No finished deals are waiting for a review."));
        }
        for (Deal deal : reviewable) {
            Label status = new Label(statusLabel(deal.getStatus()));
            status.getStyleClass().addAll("badge", "badge-live");
            VBox info = rowInfo(deal.getListing(), requestTypeLabel(deal.getDealType()), status);
            info.getChildren().add(muted("Finished " + deal.getCompletedDate().format(DEAL_TIME)));
            Button rate = new Button("Rate Seller");
            rate.getStyleClass().addAll("btn-primary", "btn-sm");
            rate.setOnAction(e -> {
                showRateDialog(deal);
                showReviews();
            });
            Button view = new Button("View item");
            view.getStyleClass().addAll("btn-outline", "btn-sm");
            view.setOnAction(e -> ListingDetailDialog.show(
                    contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), deal.getListing()));
            root.getChildren().add(actionRow(info, new HBox(8, view, rate)));
        }

        root.getChildren().add(sectionTitle("My reviews"));
        List<Review> mine = reviewService.myReviews(user);
        if (mine.isEmpty()) {
            root.getChildren().add(muted("You haven't written any reviews yet."));
        }
        for (Review review : mine) {
            Listing reviewed = reviewService.listingOf(review);
            VBox item = reviewCard(review, "for " + displayName(review.getSellerUsername()),
                    reviewed == null ? null : () -> ListingDetailDialog.show(
                            contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), reviewed));
            if (review.isHidden()) {
                item.getChildren().add(2, muted("Hidden by the admin, so it no longer counts towards the rating."));
            }
            root.getChildren().add(item);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /** The review form: 1-5 stars and text, checked on submit (A1, A2). */
    private void showRateDialog(Deal deal) {
        ToggleGroup starsGroup = new ToggleGroup();
        HBox starRow = new HBox(6);
        for (int i = 1; i <= 5; i++) {
            ToggleButton star = new ToggleButton("\u2605".repeat(i));
            star.setMinWidth(Region.USE_PREF_SIZE);
            star.setUserData(i);
            star.setToggleGroup(starsGroup);
            starRow.getChildren().add(star);
        }
        TextArea text = new TextArea();
        text.setPromptText("How was the deal with " + deal.getSellerDisplayName() + "?");
        text.setPrefRowCount(4);
        text.setWrapText(true);
        Label error = dialogError();
        VBox body = new VBox(8, new Label("Rating"), starRow, new Label("Review"), text, error);

        Dialog<ButtonType> dialog = requestDialog("Rate Seller",
                "Rate " + deal.getSellerDisplayName() + " for '" + deal.getListing().getTitle() + "'", body,
                "Submit Review");
        onConfirm(dialog, error, () -> reviewService.submitReview(SessionManager.getCurrentUser(), deal,
                starsGroup.getSelectedToggle() == null ? 0 : (Integer) starsGroup.getSelectedToggle().getUserData(),
                text.getText()));
        dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(b -> info("Thank you! Your review of " + deal.getSellerDisplayName() + " has been submitted."));
    }

    /** Report Listing (UC-1.10): a reason is required (A1); Cancel discards the report (A2). */
    private void showReportDialog(Listing listing) {
        TextArea reason = new TextArea();
        reason.setPromptText("What is wrong with this listing?");
        reason.setPrefRowCount(4);
        reason.setWrapText(true);
        Label error = dialogError();
        VBox body = new VBox(8, muted("The admin will review your report."), new Label("Reason"), reason, error);
        Dialog<ButtonType> dialog = requestDialog("Report Listing", "Report: " + listing.getTitle(), body,
                "Submit Report");
        onConfirm(dialog, error, () -> reviewService.reportListing(SessionManager.getCurrentUser(), listing,
                reason.getText()));
        dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(b -> info("Thank you. Your report has been sent to the admin."));
    }

    /** Every visible seller review, newest first, shown on the listing details (SRS UC-1.9 postconditions). */
    private VBox sellerReviews(String sellerUsername, String sellerName) {
        List<Review> visible = ListingDetailDialog.visibleSellerReviews(sellerUsername);
        VBox box = new VBox(4);
        Label heading = new Label("Reviews of " + sellerName);
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        box.getChildren().add(heading);
        if (visible.isEmpty()) {
            box.getChildren().add(muted("No reviews yet."));
        }
        for (Review review : visible) {
            box.getChildren().add(muted(stars(review.getRating()) + "  \u201c" + review.getComment() + "\u201d  \u2014 "
                    + displayName(review.getBuyerUsername())));
        }
        return box;
    }

    /** A user's name as others see it ("deleted user" after a soft delete). */
    private static String displayName(String username) {
        User user = DummyDataService.getInstance().users().find(username);
        return user == null ? username : user.getDisplayName();
    }

    /**
     * One review as a card: stars and listing title, who and when, the text, and any seller reply.
     * Shared by My Reviews and the seller profile so both show reviews the same way.
     */
    private static VBox reviewCard(Review review, String byLine) {
        return reviewCard(review, byLine, null);
    }

    /**
     * As above; when {@code onTitle} is given, the listing title is a link that runs it (e.g. to
     * show the reviewed item).
     */
    private static VBox reviewCard(Review review, String byLine, Runnable onTitle) {
        Label stars = new Label(stars(review.getRating()));
        stars.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        HBox heading = new HBox(8, stars);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (review.getListingTitle() != null) {
            if (onTitle != null) {
                Hyperlink title = new Hyperlink(review.getListingTitle());
                title.setStyle("-fx-font-weight: bold;");
                title.setOnAction(e -> onTitle.run());
                heading.getChildren().add(title);
            } else {
                Label title = new Label(review.getListingTitle());
                title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                heading.getChildren().add(title);
            }
        }
        Label comment = new Label(review.getComment() == null ? "" : review.getComment());
        comment.setWrapText(true);
        VBox item = new VBox(3, heading, muted(byLine + "  \u00b7  " + review.getCreatedAt().format(DEAL_TIME)), comment);
        if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
            Label reply = new Label("Seller replied: " + review.getSellerReply());
            reply.setWrapText(true);
            reply.setStyle("-fx-text-fill: #003974; -fx-font-style: italic;");
            item.getChildren().add(reply);
        }
        item.getStyleClass().add("notif-item");
        return item;
    }

    private static String stars(int rating) {
        return "\u2605".repeat(rating) + "\u2606".repeat(5 - rating);
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
            Button profile = new Button("Profile");
            profile.getStyleClass().addAll("btn-primary", "btn-sm");
            profile.setOnAction(e -> showSellerProfile(username, "Favourites", this::showFavourites, btnFavourites));
            Button unfollow = new Button("Unfollow");
            unfollow.getStyleClass().addAll("btn-outline", "btn-sm");
            unfollow.setOnAction(e -> {
                data.favourites().toggleFollow(buyer, seller);
                showFavourites();
            });
            HBox row = new HBox(10, info, profile, unfollow);
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
        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "";
        contentPane.getChildren().add(NotificationBoardView.build(username, BUYER_CATEGORIES,
                notificationFilter,
                f -> { notificationFilter = f; showNotifications(); },
                this::openNotification));
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
                } else if (ListingSearch.isBrowsable(listing)) {
                    showListingDetails(listing);
                } else {
                    ListingDetailDialog.show(contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), listing);
                }
            }
            case REQUEST, DEAL -> {
                Deal deal = data.deals().allDeals().stream()
                        .filter(d -> d.getId().equals(ref.getId()))
                        .findFirst()
                        .orElse(null);
                if (deal == null) {
                    showMyDeals();
                } else {
                    showDealNotification(deal);
                }
            }
            case THREAD -> {
                // A thread is one buyer's chat on one listing; the id may be "buyer|L101" or just the listing id.
                String id = ref.getId();
                Listing listing = data.listings().find(id.substring(id.indexOf('|') + 1));
                if (listing == null) {
                    new Alert(Alert.AlertType.INFORMATION, "This chat's listing no longer exists.").showAndWait();
                } else {
                    openChat(listing, "Notifications", this::showNotifications);
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
                // A buyer is only ever told about their own account.
                User me = SessionManager.getCurrentUser();
                if (me != null && me.getUsername().equalsIgnoreCase(ref.getId())) {
                    showProfile();
                }
            }
        }
    }

    /** Read-only details of the deal a notification points at (SRS UC-1.8 step 3.2). */
    private void showDealNotification(Deal deal) {
        LocalDateTime dueOrCompleted = deal.getStatus() == DealStatus.ACTIVE
                ? deal.getDueDate() : deal.getCompletedDate();
        String text = dealNotificationText(deal.getId(), deal.getDealType(), deal.getStatus(),
                deal.getListing().getTitle(), deal.getBuyerDisplayName(), deal.getSellerDisplayName(),
                deal.getStartDate(), dueOrCompleted);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Deal details");
        alert.setHeaderText(deal.getListing().getTitle());
        alert.getDialogPane().setContent(notificationBody(text));
        alert.showAndWait();
    }

    /** Read-only details of the review a notification points at (SRS UC-1.8 step 3.2). */
    private void showReviewNotification(Review review) {
        String text = reviewNotificationText(review.getRating(), review.getComment(),
                displayName(review.getBuyerUsername()), review.getCreatedAt(), review.getSellerReply());
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Review details");
        alert.setHeaderText(review.getListingTitle() == null ? "Your review" : review.getListingTitle());
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

    /**
     * The facts shown when a REQUEST/DEAL notification is opened: the deal id, type, status,
     * listing, parties, start and its due (while active) or completed date. Takes primitives so
     * the text is testable without constructing a package-private {@link Deal}.
     */
    static String dealNotificationText(String dealId, DealType type, DealStatus status, String listingTitle,
                                       String buyer, String seller, LocalDateTime start,
                                       LocalDateTime dueOrCompleted) {
        String dateLabel = status == DealStatus.ACTIVE ? "Due" : "Completed";
        return "Deal " + orDash(dealId) + "\n"
                + "Type: " + requestTypeLabel(type) + "\n"
                + "Status: " + statusLabel(status) + "\n"
                + "Listing: " + orDash(listingTitle) + "\n"
                + "Buyer: " + orDash(buyer) + "\n"
                + "Seller: " + orDash(seller) + "\n"
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

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private static String whenText(LocalDateTime dateTime) {
        return dateTime == null ? "\u2014" : dateTime.format(DEAL_TIME);
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
        HBox currentPassRow = new HBox(6, currentPass);
        PasswordVisibility.attach(currentPass, currentPassRow);
        HBox newPassRow = new HBox(6, newPass);
        PasswordVisibility.attach(newPass, newPassRow);
        HBox confirmPassRow = new HBox(6, confirmPass);
        PasswordVisibility.attach(confirmPass, confirmPassRow);
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
                new Label("Current password"), currentPassRow,
                new Label("New password"), newPassRow,
                new Label("Confirm new password"), confirmPassRow, rules,
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
