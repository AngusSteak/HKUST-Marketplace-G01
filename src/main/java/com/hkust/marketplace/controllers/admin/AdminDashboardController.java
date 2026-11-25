package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.controllers.common.ListingDetailDialog;
import com.hkust.marketplace.controllers.notifications.NotificationBoardView;
import com.hkust.marketplace.forms.ListingFormComponent;
import com.hkust.marketplace.forms.ListingFormState;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.AdminFilters;
import com.hkust.marketplace.utils.AdminService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.LocalPhotoStorage;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Admin dashboard controller: builds the listing review, users, listings, deals, ratings,
 * notifications and profile screens in one content pane. All writes go through
 * {@link AdminService}; this class only collects input and renders results.
 */
public class AdminDashboardController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private static final List<NotificationCategory> ADMIN_CATEGORIES = List.of(
            NotificationCategory.NEW_SUBMISSION, NotificationCategory.REPORTED_LISTING,
            NotificationCategory.ACCOUNT_CHANGE, NotificationCategory.ANNOUNCEMENT);

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final LocalPhotoStorage photoStorage = new LocalPhotoStorage();
    private ListingFormComponent listingForm;
    private NotificationCategory notificationFilter;

    /** Builds the dashboard and shows the listing review screen first. */
    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
        currentActiveBtn = btnReview;
        showReview();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    /** Every admin action goes through the service, which checks the session is an admin (#60). */
    private final AdminService adminService = new AdminService(DummyDataService.getInstance());

    private static User admin() {
        return SessionManager.getCurrentUser();
    }

    /** Runs an admin action, showing any refusal instead of letting it escape to JavaFX. */
    private static void run(Runnable action) {
        try {
            action.run();
        } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
        }
    }

    private static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.setStyle(style);
        return label;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static String statusText(ListingStatus status) {
        return status.name().replace('_', ' ');
    }

    private String party(String username) {
        return AdminFilters.partyLabel(DummyDataService.getInstance().users().find(username), username);
    }

    // ---------- Listing review ----------

    @FXML
    private void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));
        List<Listing> pending = DummyDataService.getInstance().listings().pending();
        if (pending.isEmpty()) {
            root.getChildren().add(label("No listings awaiting review.", "-fx-text-fill: #2e7d32; -fx-font-size: 15px;"));
        } else {
            for (Listing l : pending) root.getChildren().add(createPendingCard(l));
        }
        contentPane.getChildren().add(scroll(root));
    }

    private VBox createPendingCard(Listing l) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        Label title = label(l.getTitle(), "-fx-font-weight: bold; -fx-font-size: 16px;");
        Label meta = label(String.format("Seller: %s (@%s)  ·  %s  ·  %s  ·  %s",
                l.getSellerFullName(), l.getSellerUsername(), l.getCategory(), l.getTypeDisplay(),
                l.getMeetupLocation()), "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label submitted = label("Submitted: " + l.getSubmittedDate().format(DATE_TIME),
                "-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Button approve = new Button("Approve");
        approve.getStyleClass().addAll("btn-success", "btn-sm");
        approve.setOnAction(e -> new Alert(Alert.AlertType.CONFIRMATION, "Approve this listing?")
                .showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        run(() -> adminService.approveListing(admin(), l));
                        showReview();
                    }
                }));

        Button reject = new Button("Reject");
        reject.getStyleClass().addAll("btn-danger", "btn-sm");
        reject.setOnAction(e -> {
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("Rejection Reason");
            reasonDialog.setHeaderText("Please provide a reason for rejection");
            reasonDialog.setContentText("Reason:");
            reasonDialog.showAndWait().ifPresent(reason -> {
                if (reason.isBlank()) {
                    new Alert(Alert.AlertType.WARNING, "Reason is required.").showAndWait();
                    return;
                }
                run(() -> adminService.rejectListing(admin(), l, reason));
                showReview();
            });
        });

        Button view = new Button("View");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> ListingDetailDialog.show(
                contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), l));

        card.getChildren().addAll(title, meta, submitted, new HBox(10, view, approve, reject));
        return card;
    }

    // ---------- All listings (SRS UC-3.7) ----------

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button add = new Button("+ Add listing on behalf of a seller");
        add.getStyleClass().add("btn-primary");
        add.setOnAction(e -> showAddOnBehalf());

        TextField search = new TextField();
        search.setPromptText("Search title or seller...");
        HBox.setHgrow(search, Priority.ALWAYS);
        ComboBox<String> status = new ComboBox<>();
        status.getItems().add("All statuses");
        for (ListingStatus s : ListingStatus.values()) status.getItems().add(statusText(s));
        status.getSelectionModel().selectFirst();

        TableView<Listing> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(520);
        table.getColumns().add(listingTitleColumn());
        table.getColumns().add(textColumn("Seller", l -> l.getSellerFullName() + " (@" + l.getSellerUsername() + ")", 160));
        table.getColumns().add(textColumn("Type", l -> l.getTypeDisplay(), 100));
        table.getColumns().add(textColumn("Status", l -> statusText(l.getStatus()), 110));
        table.getColumns().add(textColumn("Price", Listing::getPriceDisplay, 90));
        table.getColumns().add(reportsColumn());
        table.getColumns().add(listingActions());

        List<Listing> all = new ArrayList<>(DummyDataService.getInstance().listings().all());
        Runnable refresh = () -> {
            String keyword = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String chosen = status.getValue();
            table.setItems(FXCollections.observableArrayList(all.stream()
                    .filter(l -> keyword.isEmpty()
                            || l.getTitle().toLowerCase(Locale.ROOT).contains(keyword)
                            || l.getSellerUsername().toLowerCase(Locale.ROOT).contains(keyword)
                            || l.getSellerFullName().toLowerCase(Locale.ROOT).contains(keyword))
                    .filter(l -> chosen == null || chosen.equals("All statuses")
                            || chosen.equals(statusText(l.getStatus())))
                    .toList()));
        };
        search.textProperty().addListener((o, a, b) -> refresh.run());
        status.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        table.getStyleClass().add("link-table");
        table.setFocusTraversable(false);
        root.getChildren().addAll(new HBox(10, add, search, status),
                label("Click a title to preview the item.", "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"), table);
        contentPane.getChildren().add(root);
    }

    private TableColumn<Listing, String> listingTitleColumn() {
        TableColumn<Listing, String> column = textColumn("Title", Listing::getTitle, 220);
        column.setCellFactory(c -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();

            {
                link.getStyleClass().add("deal-link");
                link.setOnAction(e -> {
                    Listing listing = getTableRow() == null ? null : getTableRow().getItem();
                    if (listing != null) {
                        ListingDetailDialog.show(
                                contentPane.getScene() == null ? null : contentPane.getScene().getWindow(), listing);
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

    private static <T> TableColumn<Listing, String> textColumn(String title,
                                                               java.util.function.Function<Listing, String> value,
                                                               double width) {
        TableColumn<Listing, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    private TableColumn<Listing, String> reportsColumn() {
        TableColumn<Listing, String> column = new TableColumn<>("Open reports");
        column.setPrefWidth(100);
        column.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(
                DummyDataService.getInstance().reports().openReports(c.getValue()).size())));
        return column;
    }

    private TableColumn<Listing, Void> listingActions() {
        TableColumn<Listing, Void> column = new TableColumn<>("Actions");
        column.setPrefWidth(280);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button reports = new Button("Reports");
            private final Button takeDown = new Button("Take down");
            private final HBox box = new HBox(6, edit, reports, takeDown);

            {
                edit.getStyleClass().addAll("btn-outline", "btn-sm");
                reports.getStyleClass().addAll("btn-outline", "btn-sm");
                takeDown.getStyleClass().addAll("btn-danger", "btn-sm");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                Listing listing = getTableView().getItems().get(getIndex());
                int open = DummyDataService.getInstance().reports().openReports(listing).size();
                reports.setText(open == 0 ? "Reports" : "Reports (" + open + ")");
                edit.setOnAction(e -> showEditListing(listing));
                reports.setOnAction(e -> showReports(listing));
                takeDown.setOnAction(e -> promptTakeDown(listing, AdminDashboardController.this::showAllListings));
                setGraphic(box);
            }
        });
        return column;
    }

    private void showEditListing(Listing listing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit listing");
        dialog.setHeaderText("Edit '" + listing.getTitle() + "' without changing its status.");

        TextField title = new TextField(listing.getTitle());
        TextArea description = new TextArea(listing.getDescription());
        description.setPrefRowCount(3);
        ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        category.setValue(listing.getCategory());
        category.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Condition> condition = new ComboBox<>(FXCollections.observableArrayList(Condition.values()));
        condition.setValue(listing.getCondition());
        condition.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Location> location = new ComboBox<>(FXCollections.observableArrayList(Location.values()));
        location.setValue(listing.getMeetupLocation());
        location.setMaxWidth(Double.MAX_VALUE);

        TextField price = new TextField();
        TextField days = new TextField();
        TextField hours = new TextField();
        TextField seconds = new TextField();
        if (listing instanceof SaleListing sale) {
            price.setText(sale.getPrice().amount().toPlainString());
        } else if (listing instanceof LendListing lend) {
            Duration duration = lend.getLendingDuration();
            days.setText(String.valueOf(duration.toDays()));
            hours.setText(String.valueOf(duration.toHoursPart()));
            seconds.setText(String.valueOf(duration.toSecondsPart()));
        }

        VBox form = new VBox(8,
                new Label("Title"), title,
                new Label("Description"), description,
                new Label("Category"), category,
                new Label("Condition"), condition,
                new Label("Meet-up location"), location);
        if (listing instanceof SaleListing) {
            form.getChildren().addAll(new Label("Asking price"), price);
        } else if (listing instanceof LendListing) {
            HBox durationRow = new HBox(8, days, hours, seconds);
            HBox.setHgrow(days, Priority.ALWAYS);
            HBox.setHgrow(hours, Priority.ALWAYS);
            HBox.setHgrow(seconds, Priority.ALWAYS);
            days.setPromptText("Days");
            hours.setPromptText("Hours");
            seconds.setPromptText("Seconds (min 5)");
            form.getChildren().addAll(new Label("Lending duration"), durationRow);
        }
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            Duration duration = listing instanceof LendListing
                    ? Duration.ofDays(parseLong(days.getText())).plusHours(parseLong(hours.getText()))
                    .plusSeconds(parseLong(seconds.getText()))
                    : null;
            run(() -> adminService.editListing(admin(), listing, title.getText(), description.getText(),
                    category.getValue(), condition.getValue(), location.getValue(), price.getText(), duration));
        } catch (NumberFormatException ex) {
            new Alert(Alert.AlertType.WARNING, "Lending days, hours and seconds must be whole numbers.").showAndWait();
        }
        showAllListings();
    }

    private static long parseLong(String text) {
        return text == null || text.isBlank() ? 0 : Long.parseLong(text.trim());
    }

    private void promptTakeDown(Listing listing, Runnable after) {
        TextInputDialog reasonDialog = new TextInputDialog();
        reasonDialog.setTitle("Take Down Listing");
        reasonDialog.setHeaderText("Take '" + listing.getTitle() + "' off the marketplace?\n"
                + "Open requests are declined, any active deal is cancelled and the seller is notified.");
        reasonDialog.setContentText("Reason:");
        reasonDialog.showAndWait().ifPresent(reason -> {
            run(() -> adminService.takeDownListing(admin(), listing, reason));
            after.run();
        });
    }

    private void showReports(Listing listing) {
        List<ListingReport> open = DummyDataService.getInstance().reports().openReports(listing);
        if (open.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "This listing has no open reports.").showAndWait();
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reports on '" + listing.getTitle() + "'");
        dialog.setHeaderText(open.size() + " open report(s)");
        VBox content = new VBox(10);
        content.setPrefWidth(520);
        for (ListingReport report : open) {
            VBox card = new VBox(4);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(10));
            card.getChildren().addAll(
                    label(party(report.getReporterUsername()) + " · " + report.getReportedTime().format(DATE),
                            "-fx-text-fill: #9aa0b0; -fx-font-size: 11px;"),
                    label(report.getReason(), "-fx-text-fill: #5a5a7a;"));
            content.getChildren().add(card);
        }
        // SRS UC-3.7 3.3.2: dismiss all open reports with one note, or take the listing down.
        ButtonType dismissAll = new ButtonType("Dismiss all with note", ButtonBar.ButtonData.LEFT);
        ButtonType takeDown = new ButtonType("Take down listing", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(dismissAll, takeDown, ButtonType.CLOSE);
        dialog.setOnHidden(e -> showAllListings());
        dialog.showAndWait().ifPresent(result -> {
            if (result == takeDown) {
                promptTakeDown(listing, this::showAllListings);
            } else if (result == dismissAll) {
                promptDismissReports(listing, open.size());
            }
        });
    }

    /** Asks for the resolution note until one is given (A6) or the admin cancels. */
    private void promptDismissReports(Listing listing, int count) {
        while (true) {
            TextInputDialog note = new TextInputDialog();
            note.setTitle("Dismiss reports");
            note.setHeaderText("Dismiss " + count + " open report(s) on '" + listing.getTitle()
                    + "'? The listing stays as it is.");
            note.setContentText("Resolution note:");
            Optional<String> text = note.showAndWait();
            if (text.isEmpty()) {
                return;
            }
            if (text.get().isBlank()) {
                new Alert(Alert.AlertType.WARNING, AdminService.RESOLUTION_NOTE_REQUIRED).showAndWait();
                continue;
            }
            run(() -> adminService.dismissReports(admin(), listing, text.get()));
            showAllListings();
            return;
        }
    }

    private void showAddOnBehalf() {
        setActive(btnAllListings, "Add Listing On Behalf");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        ComboBox<Seller> seller = new ComboBox<>(FXCollections.observableArrayList(
                DummyDataService.getInstance().users().byRole(UserRole.SELLER).stream()
                        .filter(User::isActive).map(u -> (Seller) u).toList()));
        seller.setPromptText("Seller who owns this listing *");
        seller.setMaxWidth(420);

        listingForm = new ListingFormComponent(() -> seller.getValue(),
                state -> submitOnBehalf(state, seller.getValue()));
        listingForm.setSubmitLabel("Save as Live");
        listingForm.setOnCancel(this::showAllListings);

        root.getChildren().addAll(label("Add on behalf of", "-fx-font-weight: bold;"), seller,
                listingForm.getView());
        contentPane.getChildren().add(scroll(root));
    }

    private void submitOnBehalf(ListingFormState state, Seller seller) {
        if (seller == null) {
            new Alert(Alert.AlertType.WARNING, "Choose the seller who owns this listing.").showAndWait();
            return;
        }
        Map<String, String> errors = state.validate(seller);
        if (!errors.isEmpty()) {
            listingForm.showErrors(errors);
            return;
        }
        try {
            adminService.addListingOnBehalf(admin(), seller, state, photoStorage);
        } catch (SecurityException | IllegalArgumentException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            return;
        }
        new Alert(Alert.AlertType.INFORMATION, "Listing saved as Live for @" + seller.getUsername() + ".").showAndWait();
        showAllListings();
    }

    // ---------- Manage users (SRS UC-3.4, UC-3.3) ----------

    @FXML
    private void showUsers() {
        showUsers("");
    }

    private void showUsers(String initialSearch) {
        setActive(btnUsers, "Manage All Users");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button add = new Button("+ Add user");
        add.getStyleClass().add("btn-primary");
        add.setOnAction(e -> showAddUser());
        TextField search = new TextField();
        search.setPromptText("Search username or name...");
        search.setText(initialSearch == null ? "" : initialSearch);
        HBox.setHgrow(search, Priority.ALWAYS);
        root.getChildren().addAll(new HBox(10, add, search));

        VBox rows = new VBox(8);
        root.getChildren().add(rows);
        Runnable refresh = () -> {
            rows.getChildren().clear();
            String keyword = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            List<User> users = DummyDataService.getInstance().users().all().stream()
                    .filter(u -> u.getRole() != UserRole.ADMIN)
                    .filter(u -> keyword.isEmpty()
                            || u.getUsername().toLowerCase(Locale.ROOT).contains(keyword)
                            || u.getFullName().toLowerCase(Locale.ROOT).contains(keyword))
                    .toList();
            if (users.isEmpty()) {
                rows.getChildren().add(label("No matching users.", "-fx-text-fill: #5a5a7a;"));
            }
            for (User u : users) rows.getChildren().add(userRow(u));
        };
        search.textProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();
        contentPane.getChildren().add(scroll(root));
    }

    private HBox userRow(User user) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        String status = !user.isActive() ? " · Deactivated" : "";
        info.getChildren().addAll(
                label(user.getFullName() + " (@" + user.getUsername() + ")", "-fx-font-weight: bold;"),
                label(user.getRole().name() + status, "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"));

        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(e -> showEditUser(user));

        Button toggle = new Button(user.isActive() ? "Deactivate" : "Reactivate");
        toggle.getStyleClass().addAll(user.isActive() ? "btn-danger" : "btn-success", "btn-sm");
        toggle.setOnAction(e -> {
            run(() -> adminService.setActive(admin(), user, !user.isActive()));
            showUsers();
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-sm");
        delete.setOnAction(e -> confirmSoftDelete(user));

        row.getChildren().addAll(info, edit, toggle, delete);
        return row;
    }

    private void showAddUser() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add user");
        ComboBox<UserRole> role = new ComboBox<>(FXCollections.observableArrayList(UserRole.BUYER, UserRole.SELLER));
        role.getSelectionModel().selectFirst();
        role.setMaxWidth(Double.MAX_VALUE);
        TextField username = new TextField();
        TextField fullName = new TextField();
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        ComboBox<String> residence = new ComboBox<>(FXCollections.observableArrayList(
                "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5", "Hall 6", "Hall 7", "Hall 8", "Hall 9",
                "UG Hall 1", "UG Hall 2", "Off-campus"));
        residence.setMaxWidth(Double.MAX_VALUE);
        TextArea bio = new TextArea();
        bio.setPrefRowCount(2);
        ComboBox<Location> meetup = new ComboBox<>(FXCollections.observableArrayList(Location.values()));
        meetup.setMaxWidth(Double.MAX_VALUE);
        VBox residenceBox = new VBox(4, new Label("Residence hall"), residence);
        VBox sellerBox = new VBox(8, new Label("Bio"), bio, new Label("Preferred meet-up"), meetup);
        sellerBox.setVisible(false);
        sellerBox.setManaged(false);
        role.valueProperty().addListener((o, a, b) -> {
            boolean seller = b == UserRole.SELLER;
            residenceBox.setVisible(!seller);
            residenceBox.setManaged(!seller);
            sellerBox.setVisible(seller);
            sellerBox.setManaged(seller);
        });

        VBox form = new VBox(8,
                new Label("Role"), role,
                new Label("Username"), username,
                new Label("Full name"), fullName,
                new Label("Password"), password,
                new Label("Confirm password"), confirm,
                residenceBox, sellerBox);
        form.setPrefWidth(380);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        if (!password.getText().equals(confirm.getText())) {
            new Alert(Alert.AlertType.WARNING, "Passwords do not match.").showAndWait();
            return;
        }
        UserRole chosen = role.getValue() == null ? UserRole.BUYER : role.getValue();
        try {
            adminService.addAccount(admin(), chosen, username.getText(), fullName.getText(), password.getText(),
                    chosen == UserRole.BUYER ? residence.getValue() : null, bio.getText(),
                    chosen == UserRole.SELLER ? meetup.getValue() : null);
            new Alert(Alert.AlertType.INFORMATION, "Account @" + username.getText().trim() + " created.").showAndWait();
            showUsers();
        } catch (SecurityException | IllegalArgumentException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
        }
    }

    private void showEditUser(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit @" + user.getUsername());
        TextField fullName = new TextField(user.getFullName());
        VBox form = new VBox(8, new Label("Full name"), fullName);
        final ComboBox<String> residence;
        final TextArea bio;
        final ComboBox<Location> meetup;
        if (user instanceof Buyer buyer) {
            residence = new ComboBox<>(FXCollections.observableArrayList(
                    "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5", "Hall 6", "Hall 7", "Hall 8", "Hall 9",
                    "UG Hall 1", "UG Hall 2", "Off-campus"));
            residence.setValue(buyer.getResidenceHall());
            residence.setMaxWidth(Double.MAX_VALUE);
            form.getChildren().addAll(new Label("Residence hall"), residence);
            bio = null;
            meetup = null;
        } else {
            Seller seller = (Seller) user;
            bio = new TextArea(seller.getBio());
            bio.setPrefRowCount(2);
            meetup = new ComboBox<>(FXCollections.observableArrayList(Location.values()));
            meetup.setValue(seller.getPreferredMeetup().stream().findFirst().orElse(null));
            meetup.setMaxWidth(Double.MAX_VALUE);
            form.getChildren().addAll(new Label("Bio"), bio, new Label("Preferred meet-up"), meetup);
            residence = null;
        }
        form.setPrefWidth(380);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            adminService.updateAccount(admin(), user, fullName.getText(),
                    residence == null ? null : residence.getValue(),
                    bio == null ? null : bio.getText(),
                    meetup == null ? null : meetup.getValue());
            new Alert(Alert.AlertType.INFORMATION, "Account updated.").showAndWait();
            showUsers();
        } catch (SecurityException | IllegalArgumentException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
        }
    }

    private void confirmSoftDelete(User user) {
        AdminService.AccountActivity activity = adminService.accountActivity(admin(), user);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Soft-delete account");
        dialog.setHeaderText("Delete @" + user.getUsername() + "?");
        VBox content = new VBox(8);
        content.setPrefWidth(460);
        content.getChildren().addAll(
                label("This closes the account and everything still open on it:", "-fx-text-fill: #5a5a7a;"),
                label("Listings: " + activity.listings().size(), "-fx-font-weight: bold;"),
                label("Requests: " + activity.requests().size(), "-fx-font-weight: bold;"),
                label("Deals: " + activity.deals().size(), "-fx-font-weight: bold;"),
                label("Chats: " + activity.chats().size(), "-fx-font-weight: bold;"));
        VBox detail = new VBox(2);
        for (Listing listing : activity.listings()) {
            detail.getChildren().add(label("· Listing " + listing.getId() + ": " + listing.getTitle()
                    + " (" + statusText(listing.getStatus()) + ")", "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"));
        }
        for (DealRequest request : activity.requests()) {
            detail.getChildren().add(label("· Request " + request.getId() + ": " + request.getListing().getTitle()
                    + " (" + request.getStatus() + ")", "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"));
        }
        for (Deal deal : activity.deals()) {
            detail.getChildren().add(label("· Deal " + deal.getId() + ": " + deal.getListing().getTitle()
                    + " (" + deal.getStatus() + ")", "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"));
        }
        for (ChatThread chat : activity.chats()) {
            detail.getChildren().add(label("· Chat with @" + chat.getBuyer().getUsername() + ": "
                    + chat.getListing().getTitle(), "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"));
        }
        if (!detail.getChildren().isEmpty()) {
            ScrollPane detailScroll = new ScrollPane(detail);
            detailScroll.setFitToWidth(true);
            detailScroll.setPrefHeight(180);
            content.getChildren().add(detailScroll);
        }
        ButtonType delete = new ButtonType("Delete account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(delete, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != delete) return;
        run(() -> adminService.softDeleteAccount(admin(), user));
        showUsers();
    }

    // ---------- All deals (SRS UC-3.5) ----------

    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals Record");
        contentPane.getChildren().clear();

        VBox root = new VBox(10);
        root.setPadding(new Insets(4));

        TextField search = new TextField();
        search.setPromptText("Search title, buyer or seller...");
        HBox.setHgrow(search, Priority.ALWAYS);
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("All types", "SOLD", "LENT", "GIVEN"));
        type.getSelectionModel().selectFirst();
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("All statuses"));
        for (DealStatus s : DealStatus.values()) status.getItems().add(s.name());
        status.getSelectionModel().selectFirst();
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");

        TableView<Deal> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(500);
        table.getColumns().add(dealTitleColumn());
        table.getColumns().add(dealColumn("Buyer", d -> party(d.getBuyerUsername()), 160));
        table.getColumns().add(dealColumn("Seller", d -> party(d.getSellerUsername()), 160));
        table.getColumns().add(dealColumn("Type", d -> d.getDealType().name(), 80));
        table.getColumns().add(dealColumn("Start", d -> d.getStartDate().format(DATE), 100));
        table.getColumns().add(dealColumn("Return / complete", d -> dealEnd(d), 120));
        table.getColumns().add(dealColumn("Status", d -> d.getStatus().name(), 100));

        Runnable refresh = () -> table.setItems(FXCollections.observableArrayList(
                DummyDataService.getInstance().deals().allDeals().stream()
                        .filter(d -> AdminFilters.matchesDeal(d, search.getText(), type.getValue(),
                                status.getValue(), from.getValue(), to.getValue()))
                        .sorted(Comparator.comparing(Deal::getStartDate).reversed())
                        .toList()));
        search.textProperty().addListener((o, a, b) -> refresh.run());
        type.valueProperty().addListener((o, a, b) -> refresh.run());
        status.valueProperty().addListener((o, a, b) -> refresh.run());
        from.valueProperty().addListener((o, a, b) -> refresh.run());
        to.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        table.getStyleClass().add("link-table");
        table.setFocusTraversable(false);

        root.getChildren().addAll(new HBox(10, search, type, status, from, to),
                label("Click a title to view details.", "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;"), table);
        contentPane.getChildren().add(root);
    }

    private static TableColumn<Deal, String> dealColumn(String title,
                                                         java.util.function.Function<Deal, String> value,
                                                         double width) {
        TableColumn<Deal, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    private TableColumn<Deal, String> dealTitleColumn() {
        TableColumn<Deal, String> column = dealColumn("Title", d -> d.getListing().getTitle(), 200);
        column.setCellFactory(c -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();

            {
                link.getStyleClass().add("deal-link");
                link.setOnAction(e -> {
                    Deal deal = getTableRow() == null ? null : getTableRow().getItem();
                    if (deal != null) {
                        showDealDetails(deal);
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

    private static String dealEnd(Deal deal) {
        if (deal.getCompletedDate() != null) return deal.getCompletedDate().format(DATE);
        if (deal.getDueDate() != null) return "Due " + deal.getDueDate().format(DATE);
        return "—";
    }

    private void showDealDetails(Deal deal) {
        VBox content = new VBox(6);
        content.setPrefWidth(460);
        content.getChildren().addAll(
                label(deal.getListing().getTitle(), "-fx-font-weight: bold; -fx-font-size: 15px;"),
                label("Deal " + deal.getId() + " · " + deal.getDealType() + " · " + deal.getStatus(),
                        "-fx-text-fill: #5a5a7a;"),
                label("Buyer: " + party(deal.getBuyerUsername()), ""),
                label("Seller: " + party(deal.getSellerUsername()), ""),
                label("Start: " + deal.getStartDate().format(DATE_TIME), ""),
                label("Return / due: " + dealEnd(deal), ""),
                new Separator(),
                label("Listing", "-fx-font-weight: bold;"),
                label("Status: " + statusText(deal.getListing().getStatus())
                        + " · " + deal.getListing().getCategory()
                        + " · " + deal.getListing().getTypeDisplay(), "-fx-text-fill: #5a5a7a;"),
                label("Request " + deal.getRequest().getId() + " · " + deal.getRequest().getStatus(),
                        "-fx-text-fill: #5a5a7a;"),
                label("Submitted: " + deal.getRequest().getSubmittedTime().format(DATE_TIME),
                        "-fx-text-fill: #5a5a7a;"));
        if (deal.getRequest() instanceof PriceOffer offer) {
            content.getChildren().add(label("Offer: " + offer.getOfferedPrice().toPlainString()
                    + (offer.getCounterPrice() == null ? "" : " · Counter: " + offer.getCounterPrice().toPlainString()),
                    "-fx-text-fill: #5a5a7a;"));
        } else if (deal.getRequest() instanceof BorrowRequest borrow) {
            content.getChildren().add(label("Borrow: " + borrow.getStartDate().format(DATE)
                    + " to " + borrow.getEndDate().format(DATE), "-fx-text-fill: #5a5a7a;"));
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Deal details");
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    // ---------- Seller ratings (SRS UC-3.8) ----------

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Review> flagged = DummyDataService.getInstance().reviews().all().stream()
                .filter(r -> r.isFlagged() && !r.isHidden())
                .toList();
        root.getChildren().add(sectionLabel("Flagged reviews"));
        if (flagged.isEmpty()) {
            root.getChildren().add(label("No flagged reviews await review.", "-fx-text-fill: #5a5a7a;"));
        } else {
            for (Review review : flagged) root.getChildren().add(reviewRow(review));
        }

        root.getChildren().addAll(new Separator(), sectionLabel("Seller overview"));
        TextField sellerSearch = new TextField();
        sellerSearch.setPromptText("Filter by seller...");
        root.getChildren().add(sellerSearch);
        VBox sellers = new VBox(6);
        root.getChildren().add(sellers);
        Runnable refreshSellers = () -> {
            sellers.getChildren().clear();
            String keyword = sellerSearch.getText() == null ? "" : sellerSearch.getText().trim().toLowerCase(Locale.ROOT);
            for (User user : DummyDataService.getInstance().users().byRole(UserRole.SELLER)) {
                if (!keyword.isEmpty()
                        && !user.getUsername().toLowerCase(Locale.ROOT).contains(keyword)
                        && !user.getFullName().toLowerCase(Locale.ROOT).contains(keyword)) continue;
                OptionalDouble average = DummyDataService.getInstance().reviews().averageRating(user.getUsername());
                int count = DummyDataService.getInstance().reviews().visibleCount(user.getUsername());
                String avg = average.isPresent() ? String.format("%.1f / 5", average.getAsDouble()) : "No rating yet";
                HBox row = new HBox(10);
                row.getStyleClass().add("card");
                row.setPadding(new Insets(10));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.getChildren().addAll(
                        label(user.getFullName() + " (@" + user.getUsername() + ")", "-fx-font-weight: bold;"),
                        spacer,
                        label(avg + " · " + count + " visible review(s)", "-fx-text-fill: #5a5a7a;"));
                sellers.getChildren().add(row);
            }
        };
        sellerSearch.textProperty().addListener((o, a, b) -> refreshSellers.run());
        refreshSellers.run();

        root.getChildren().addAll(new Separator(), sectionLabel("All reviews"));
        TextField reviewSearch = new TextField();
        reviewSearch.setPromptText("Filter by seller, buyer or text...");
        ComboBox<String> score = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5 stars", "4 stars", "3 stars", "2 stars", "1 star"));
        score.getSelectionModel().selectFirst();
        ComboBox<String> visibility = new ComboBox<>(FXCollections.observableArrayList(
                "All reviews", "Visible", "Hidden", "Flagged"));
        visibility.getSelectionModel().selectFirst();
        VBox reviews = new VBox(8);
        Runnable refreshReviews = () -> {
            reviews.getChildren().clear();
            String keyword = reviewSearch.getText();
            int scoreIndex = score.getSelectionModel().getSelectedIndex();
            String shown = visibility.getValue();
            List<Review> found = DummyDataService.getInstance().reviews().all().stream()
                    .filter(r -> AdminFilters.matchesReview(r, keyword, scoreIndex, shown))
                    .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                    .toList();
            if (found.isEmpty()) reviews.getChildren().add(label("No matching reviews.", "-fx-text-fill: #5a5a7a;"));
            for (Review review : found) reviews.getChildren().add(reviewRow(review));
        };
        reviewSearch.textProperty().addListener((o, a, b) -> refreshReviews.run());
        score.valueProperty().addListener((o, a, b) -> refreshReviews.run());
        visibility.valueProperty().addListener((o, a, b) -> refreshReviews.run());
        refreshReviews.run();

        root.getChildren().addAll(new HBox(10, reviewSearch, score, visibility), reviews);
        contentPane.getChildren().add(scroll(root));
    }

    private HBox reviewRow(Review review) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        String state = review.isHidden() ? " · hidden" : review.isFlagged() ? " · flagged" : "";
        info.getChildren().addAll(
                label("★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating())
                        + "  " + party(review.getSellerUsername()) + " by " + party(review.getBuyerUsername())
                        + state, "-fx-font-weight: bold;"),
                label(review.getComment() == null ? "" : review.getComment(), "-fx-text-fill: #5a5a7a;"),
                label(review.getCreatedAt().format(DATE_TIME), "-fx-text-fill: #9aa0b0; -fx-font-size: 11px;"));
        Button hide = new Button(review.isHidden() ? "Unhide" : "Hide");
        hide.getStyleClass().addAll("btn-outline", "btn-sm");
        hide.setOnAction(e -> {
            run(() -> adminService.setReviewHidden(admin(), review, !review.isHidden()));
            showRatings();
        });
        row.getChildren().addAll(info, hide);
        return row;
    }

    // ---------- Notifications (SRS UC-3.6) ----------

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        contentPane.getChildren().clear();
        User user = admin();
        String username = user != null ? user.getUsername() : "";
        contentPane.getChildren().add(NotificationBoardView.build(username, ADMIN_CATEGORIES,
                notificationFilter,
                f -> { notificationFilter = f; showNotifications(); },
                this::openNotification));
    }

    private void openNotification(Notification notification) {
        try {
            DummyDataService.getInstance().notifications().markRead(notification, true);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
        }
        NotificationRef ref = notification.getRelatedRef();
        if (ref == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(notification.getTitle());
            alert.setHeaderText(notification.getCategory().getLabel());
            alert.setContentText(notification.getMessage());
            alert.showAndWait();
            showNotifications();
            return;
        }
        switch (ref.getTarget()) {
            case LISTING -> {
                Listing listing = DummyDataService.getInstance().listings().find(ref.getId());
                if (listing == null) {
                    new Alert(Alert.AlertType.INFORMATION, "That listing no longer exists.").showAndWait();
                    showAllListings();
                } else if (listing.getStatus() == ListingStatus.PENDING_REVIEW) {
                    showReview();
                } else {
                    showAllListings();
                    if (notification.getCategory() == NotificationCategory.REPORTED_LISTING) {
                        showReports(listing);
                    }
                }
            }
            case USER -> showUsers(ref.getId());
            case REVIEW -> showRatings();
            default -> {
                new Alert(Alert.AlertType.INFORMATION, notification.getMessage()).showAndWait();
                showNotifications();
            }
        }
    }

    // ---------- Admin profile (SRS UC-3.4) ----------

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();

        VBox form = new VBox(10);
        form.setMaxWidth(420);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.getChildren().add(sectionLabel("Admin Profile"));

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");
        PasswordField next = new PasswordField();
        next.setPromptText("New password");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        Button save = new Button("Save");
        save.getStyleClass().add("btn-success");
        save.setOnAction(e -> {
            try {
                List<String> changed = adminService.updateProfile(admin(), name.getText(),
                        current.getText(), next.getText(), confirm.getText());
                welcomeLabel.setText("Admin: " + admin().getFullName());
                if (changed.isEmpty()) {
                    new Alert(Alert.AlertType.INFORMATION, "Nothing changed.").showAndWait();
                } else {
                    new Alert(Alert.AlertType.INFORMATION,
                            "Saved: " + String.join(" and ", changed) + ".").showAndWait();
                }
                showProfile();
            } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            }
        });

        form.getChildren().addAll(
                new Label("Full name"), name,
                new Label("Change password (leave all three empty to keep it)"),
                current, next, confirm, save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
