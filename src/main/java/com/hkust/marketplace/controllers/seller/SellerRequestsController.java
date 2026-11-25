package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SellerService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Handles the standalone seller requests screen (offers and borrow requests per listing). */
public class SellerRequestsController {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private final SellerService sellerService = new SellerService(DummyDataService.getInstance());
    private final ChatService chatService = new ChatService(DummyDataService.getInstance());

    @FXML private VBox requestsBox;
    @FXML private Label feedbackLabel;
    @FXML private Button backButton;
    @FXML private Button refreshButton;

    private Consumer<ChatThread> onOpenChat;
    private Runnable onBackToChat;
    private Listing filter;

    /** Sets what runs when the seller opens a chat from a request. */
    public void setOnOpenChat(Consumer<ChatThread> onOpenChat) {
        this.onOpenChat = onOpenChat;
    }

    void setOnBackToChat(Runnable onBackToChat) {
        this.onBackToChat = onBackToChat;
        boolean show = onBackToChat != null;
        backButton.setVisible(show);
        backButton.setManaged(show);
    }

    @FXML
    private void handleBackToChat() {
        if (onBackToChat != null) {
            onBackToChat.run();
        }
    }

    /** Re-queries and re-renders the requests, keeping the current filtered-vs-all mode. */
    @FXML
    private void handleRefresh() {
        render();
    }

    static String backToChatLabel() {
        return "\u2190 Back to Chat";
    }

    /** Shows the requests for one listing only. */
    public void renderFiltered(Listing listing) {
        this.filter = Objects.requireNonNull(listing, "listing");
        render();
    }

    /** Prepares the header once the FXML is loaded. */
    @FXML
    public void initialize() {
        backButton.setText(backToChatLabel());
        backButton.getStyleClass().addAll("btn-outline", "btn-sm");
    }

    /** Draws the request cards for the signed-in seller. */
    public void render() {
        User user = SessionManager.getCurrentUser();
        requestsBox.getChildren().clear();
        hideFeedback();
        if (!(user instanceof Seller)) {
            showFeedback("Log in as a seller to manage requests.");
            return;
        }
        if (filter != null) {
            renderFilteredListing(user);
            return;
        }
        LinkedHashMap<Listing, List<DealRequest>> groups = sellerService.pendingRequestsByListing(user);
        if (groups.isEmpty()) {
            Label empty = new Label("No requests are waiting for a response.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            requestsBox.getChildren().add(empty);
            return;
        }
        for (Map.Entry<Listing, List<DealRequest>> entry : groups.entrySet()) {
            requestsBox.getChildren().add(buildListingGroup(entry.getKey(), entry.getValue(), user));
        }
    }

    private void renderFilteredListing(User user) {
        List<DealRequest> requests = sellerService.pendingRequestsForListing(user, filter);
        if (requests.isEmpty()) {
            Label empty = new Label("No requests are waiting for a response on '" + filter.getTitle() + "'.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            requestsBox.getChildren().add(empty);
            return;
        }
        requestsBox.getChildren().add(buildDeclineAllBar(filter, requests.size(), user));
        requestsBox.getChildren().add(buildListingGroup(filter, requests, user));
    }

    private HBox buildDeclineAllBar(Listing listing, int count, User user) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_RIGHT);
        Button declineAll = new Button("Decline all (" + count + ")");
        declineAll.getStyleClass().addAll("btn-danger", "btn-sm");
        declineAll.setOnAction(e -> confirmDeclineAll(listing, count, user));
        bar.getChildren().add(declineAll);
        return bar;
    }

    private void confirmDeclineAll(Listing listing, int count, User user) {
        String noun = count == 1 ? " offer" : " offers";
        String message = "Decline all " + count + noun + " on '" + listing.getTitle() + "'?\n\n"
                + "All " + count + " buyer" + (count == 1 ? "" : "s") + " will be notified.";
        confirm("Decline all offers", message, () -> {
            try {
                sellerService.declineAllForListing(user, listing);
                render();
                showFeedback("Declined " + count + noun + " on '" + listing.getTitle() + "'.");
            } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
                showFeedback(readable(ex));
            } catch (RuntimeException ex) {
                showFeedback("Could not complete the action: " + readable(ex));
            }
        });
    }

    private VBox buildListingGroup(Listing listing, List<DealRequest> requests, User user) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label(listing.getTitle());
        title.getStyleClass().add("section-title");
        Label meta = new Label(listing.getTypeDisplay() + "  ·  " + listing.getPriceDisplay()
                + "  ·  " + requests.size() + " open request" + (requests.size() == 1 ? "" : "s"));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(title, meta);

        for (DealRequest request : requests) {
            card.getChildren().add(buildRequestRow(request, user));
        }
        return card;
    }

    private VBox buildRequestRow(DealRequest request, User user) {
        VBox row = new VBox(8);
        row.getStyleClass().add("offer-card");
        row.setPadding(new Insets(10, 12, 10, 12));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label who = new Label(request.getBuyerDisplayName());
        who.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label terms = new Label(requestTerms(request));
        terms.setStyle("-fx-text-fill: #003974; -fx-font-size: 13px;");
        Label status = new Label(statusText(request));
        status.getStyleClass().addAll("badge", statusStyle(request));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(who, terms, spacer, status);

        Label submitted = new Label("Submitted " + request.getSubmittedTime().format(SHORT_DATE));
        submitted.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        VBox editorHost = new VBox(8);
        row.getChildren().addAll(top, submitted, editorHost);
        editorHost.getChildren().add(buildActionBar(request, user, editorHost));
        return row;
    }

    private HBox buildActionBar(DealRequest request, User user, VBox editorHost) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);

        boolean countered = request.getStatus() == RequestStatus.COUNTERED;
        if (countered) {
            Label awaiting = new Label("Awaiting buyer");
            awaiting.setStyle("-fx-text-fill: #ed6c02; -fx-font-size: 12px; -fx-font-weight: bold;");
            bar.getChildren().add(awaiting);
        } else {
            Button accept = new Button("Accept");
            accept.getStyleClass().addAll("btn-primary", "btn-sm");
            accept.setOnAction(e -> confirmAccept(request, user));
            bar.getChildren().add(accept);
        }

        Button decline = new Button("Decline");
        decline.getStyleClass().addAll("btn-outline", "btn-sm");
        decline.setOnAction(e -> confirmDecline(request, user));
        bar.getChildren().add(decline);

        if (!countered && canCounter(request)) {
            Button counter = new Button("Counter");
            counter.getStyleClass().addAll("btn-gold", "btn-sm");
            counter.setOnAction(e -> {
                editorHost.getChildren().clear();
                editorHost.getChildren().add(buildCounterEditor(request, user, editorHost));
            });
            bar.getChildren().add(counter);
        }

        Button openChat = new Button("Open chat");
        openChat.getStyleClass().addAll("btn-outline", "btn-sm");
        openChat.setOnAction(e -> openChat(request, user));
        bar.getChildren().add(openChat);
        return bar;
    }

    private static boolean canCounter(DealRequest request) {
        return request instanceof PriceOffer || request instanceof BorrowRequest;
    }

    private VBox buildCounterEditor(DealRequest request, User user, VBox editorHost) {
        VBox editor = new VBox(8);
        editor.setPadding(new Insets(8, 0, 0, 0));

        Label heading = new Label(request instanceof PriceOffer ? "Counter Price" : "Counter Period");
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        Label validation = new Label();
        validation.getStyleClass().add("input-error-label");
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);

        HBox fields = new HBox(8);
        fields.setAlignment(Pos.CENTER_LEFT);
        TextField first = new TextField();
        final TextField second;
        if (request instanceof PriceOffer offer) {
            second = null;
            first.setPromptText("e.g. " + offer.agreedPrice().toPlainString());
            first.setPrefWidth(160);
            fields.getChildren().addAll(new Label("Price (HK$)"), first);
        } else {
            BorrowRequest borrow = (BorrowRequest) request;
            first.setPromptText(borrow.agreedStart().format(PERIOD_FORMAT));
            second = new TextField();
            second.setPromptText(borrow.agreedEnd().format(PERIOD_FORMAT));
            first.setPrefWidth(170);
            second.setPrefWidth(170);
            fields.getChildren().addAll(new Label("Start"), first, new Label("End"), second);
        }
        editor.getChildren().addAll(heading, fields, validation);

        Button confirm = new Button("Confirm");
        confirm.getStyleClass().addAll("btn-primary", "btn-sm");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-outline", "btn-sm");
        cancel.setOnAction(e -> editorHost.getChildren().setAll(buildActionBar(request, user, editorHost)));
        HBox buttons = new HBox(8, confirm, cancel);
        editor.getChildren().add(buttons);

        confirm.setOnAction(e -> submitCounter(request, user, first, second, validation));
        return editor;
    }

    private void submitCounter(DealRequest request, User user, TextField first,
                               TextField second, Label validation) {
        validation.setVisible(false);
        validation.setManaged(false);
        first.getStyleClass().remove("input-error");
        if (second != null) {
            second.getStyleClass().remove("input-error");
        }
        try {
            if (request instanceof PriceOffer offer) {
                sellerService.counterPriceOffer(user, offer, first.getText());
            } else {
                BorrowRequest borrow = (BorrowRequest) request;
                LocalDateTime start = LocalDateTime.parse(first.getText().trim(), PERIOD_FORMAT);
                LocalDateTime end = LocalDateTime.parse(second.getText().trim(), PERIOD_FORMAT);
                sellerService.counterBorrowRequest(user, borrow, start, end);
            }
            render();
            showFeedback("Counter sent to " + request.getBuyerDisplayName() + " — awaiting buyer.");
        } catch (DateTimeParseException ex) {
            showInlineError(validation, first,
                    "Enter dates as yyyy-MM-dd HH:mm (e.g. 2026-12-01 09:00).");
        } catch (NumberFormatException ex) {
            showInlineError(validation, first, "Enter a valid counter price (e.g. 120 or 99.50).");
        } catch (IllegalArgumentException ex) {
            showInlineError(validation, first, readable(ex));
        } catch (IllegalStateException | SecurityException ex) {
            showInlineError(validation, first, readable(ex));
        } catch (RuntimeException ex) {
            showFeedback("Could not complete the action: " + readable(ex));
        }
    }

    private void confirmAccept(DealRequest request, User user) {
        String message = "Accept the request from " + request.getBuyerDisplayName()
                + " for '" + request.getListing().getTitle() + "'?\n\n"
                + "This creates the deal and declines the other open requests on this listing.";
        confirm("Accept request", message, () -> {
            try {
                sellerService.acceptRequest(user, request);
                render();
                showFeedback("Request accepted. Deal created and other offers declined.");
            } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
                showFeedback(readable(ex));
            } catch (RuntimeException ex) {
                showFeedback("Could not complete the action: " + readable(ex));
            }
        });
    }

    private void confirmDecline(DealRequest request, User user) {
        String message = "Decline the request from " + request.getBuyerDisplayName()
                + " for '" + request.getListing().getTitle() + "'?";
        confirm("Decline request", message, () -> {
            try {
                sellerService.declineRequest(user, request);
                render();
                showFeedback("Request declined.");
            } catch (SecurityException | IllegalStateException | IllegalArgumentException ex) {
                showFeedback(readable(ex));
            } catch (RuntimeException ex) {
                showFeedback("Could not complete the action: " + readable(ex));
            }
        });
    }

    private static void confirm(String title, String message, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(bt -> action.run());
    }

    private void openChat(DealRequest request, User user) {
        if (onOpenChat == null) {
            showFeedback("Chat is not available here.");
            return;
        }
        if (!(user instanceof Seller seller)) {
            showFeedback("Log in as a seller to open chats.");
            return;
        }
        ChatThread thread = chatService.threadsForSeller(seller).stream()
                .filter(t -> t.getListing().getId().equals(request.getListing().getId())
                        && t.getBuyer().getUsername().equalsIgnoreCase(request.getBuyerUsername()))
                .findFirst().orElse(null);
        if (thread == null) {
            showFeedback("No conversation with " + request.getBuyerDisplayName()
                    + " for this item yet.");
            return;
        }
        onOpenChat.accept(thread);
    }

    private static String requestTerms(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return "Price offer: " + new Money(offer.getOfferedPrice()).getDisplay();
        }
        if (request instanceof BorrowRequest borrow) {
            return "Borrow: " + borrow.getStartDate().format(SHORT_DATE)
                    + " – " + borrow.getEndDate().format(SHORT_DATE);
        }
        return "Giveaway claim";
    }

    private static String statusText(DealRequest request) {
        if (request.getStatus() == RequestStatus.COUNTERED) {
            if (request instanceof PriceOffer offer) {
                return "Countered · Counter Price " + new Money(offer.agreedPrice()).getDisplay();
            }
            if (request instanceof BorrowRequest borrow) {
                return "Countered · Counter Period " + borrow.agreedStart().format(SHORT_DATE)
                        + " – " + borrow.agreedEnd().format(SHORT_DATE);
            }
        }
        return request.getStatus().name().replace('_', ' ');
    }

    private static String statusStyle(DealRequest request) {
        return request.getStatus() == RequestStatus.COUNTERED ? "badge-sold" : "badge-pending";
    }

    private static String readable(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? "That action could not be completed. Please check the details and try again."
                : message;
    }

    private static void showInlineError(Label validation, TextField field, String message) {
        validation.setText(message);
        validation.setVisible(true);
        validation.setManaged(true);
        if (!field.getStyleClass().contains("input-error")) {
            field.getStyleClass().add("input-error");
        }
    }

    private void showFeedback(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}
