package com.hkust.marketplace.controllers.chat;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.RequestStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.ChatService;
import com.hkust.marketplace.utils.DurationText;
import com.hkust.marketplace.utils.MessageSearchFilter;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the one chat view shared by the buyer and seller portals, so both sides see the
 * same conversation record, the same offers-closed banner and the same send behaviour.
 */
public final class ChatViewBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private ChatViewBuilder() {
    }

    /**
     * Builds the chat view for {@code thread} as seen by {@code currentUser}. Whether a send
     * is allowed is decided by {@link ChatService}, not by hiding the input alone.
     */
    public static VBox build(ChatService chatService, User currentUser, ChatThread thread) {
        return build(chatService, currentUser, thread, null);
    }

    /**
     * Builds the chat view for {@code thread}, optionally letting the caller act on a formal
     * offer (the seller portal opens the matching request). A {@code null} callback keeps the
     * buyer portal's behaviour unchanged.
     */
    public static VBox build(ChatService chatService, User currentUser, ChatThread thread,
                             Consumer<DealRequest> onViewRequest) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        root.getChildren().add(header(thread));

        Runnable[] refresh = new Runnable[1];

        boolean offersClosed = !chatService.acceptsOffers(thread);
        Label banner = new Label(ChatService.OFFERS_CLOSED_BANNER);
        banner.getStyleClass().add("chat-banner");
        banner.setWrapText(true);
        banner.setVisible(offersClosed);
        banner.setManaged(offersClosed);
        root.getChildren().add(banner);

        // Search looks only inside this thread (SRS UC-1.5 step 3.3); matches replace the
        // message list until the search is cleared.
        TextField keyword = new TextField();
        ComboBox<MessageSearchFilter> kind = new ComboBox<>(
                FXCollections.observableArrayList(MessageSearchFilter.values()));
        Label searchStatus = new Label();
        searchStatus.getStyleClass().add("subtitle-label");
        Hyperlink clearSearch = new Hyperlink("Clear");
        HBox searchInfo = new HBox(6, searchStatus, clearSearch);
        searchInfo.setAlignment(Pos.CENTER_LEFT);
        searchInfo.setVisible(false);
        searchInfo.setManaged(false);
        root.getChildren().add(searchPanel(keyword, kind, () -> refresh[0].run()));
        root.getChildren().add(searchInfo);
        clearSearch.setOnAction(e -> {
            keyword.clear();
            kind.setValue(MessageSearchFilter.ALL);
            refresh[0].run();
        });

        VBox messageList = new VBox(8);
        messageList.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // Sending clears any search, so the new message is never hidden by it.
        HBox composerRow = composer(chatService, currentUser, thread, () -> {
            keyword.clear();
            kind.setValue(MessageSearchFilter.ALL);
            refresh[0].run();
        });
        root.getChildren().add(composerRow);

        Runnable rerender = () -> {
            messageList.getChildren().clear();
            String query = keyword.getText() == null ? "" : keyword.getText().trim();
            boolean searching = !query.isEmpty() || kind.getValue() != MessageSearchFilter.ALL;
            List<Message> history = searching
                    ? chatService.searchThread(currentUser, thread, query, kind.getValue())
                    : chatService.history(thread);
            searchInfo.setVisible(searching);
            searchInfo.setManaged(searching);
            if (searching) {
                searchStatus.setText(searchSummary(history.size(), query, kind.getValue()));
            }
            if (history.isEmpty()) {
                // A3: nothing in this thread matches the search.
                Label empty = new Label(searching ? "No messages found." : "No messages yet.");
                empty.getStyleClass().add("subtitle-label");
                messageList.getChildren().add(empty);
            } else {
                for (Message message : history) {
                    messageList.getChildren().add(
                            messageRow(message, currentUser, chatService, () -> refresh[0].run(),
                                    onViewRequest));
                }
            }
            // Messages always stay open; only the banner and the Make Offer button follow the listing.
            boolean nowClosed = !chatService.acceptsOffers(thread);
            banner.setVisible(nowClosed);
            banner.setManaged(nowClosed);
            javafx.scene.Node makeOffer = composerRow.lookup("#" + MAKE_OFFER_ID);
            if (makeOffer != null) {
                makeOffer.setVisible(!nowClosed);
                makeOffer.setManaged(!nowClosed);
            }
            scroll.setVvalue(1.0);
        };
        refresh[0] = rerender;
        rerender.run();
        return root;
    }

    private static VBox header(ChatThread thread) {
        VBox header = new VBox(4);
        header.getStyleClass().add("card");
        Label title = new Label(thread.getListing().getTitle());
        title.getStyleClass().add("section-title");
        Label participants = new Label("Buyer: " + thread.getBuyer().getNameWithUsername()
                + "   \u00b7   Seller: " + thread.getSeller().getNameWithUsername()
                + "   \u00b7   " + thread.getListing().getStatus().name().replace('_', ' '));
        participants.getStyleClass().add("subtitle-label");
        header.getChildren().addAll(title, participants);
        return header;
    }

    private static HBox messageRow(Message message, User currentUser,
                                   ChatService chatService, Runnable refresh,
                                   Consumer<DealRequest> onViewRequest) {
        return message.isFormalOffer()
                ? formalOfferRow(message, currentUser, chatService, refresh, onViewRequest)
                : textBubbleRow(message, currentUser);
    }

    private static HBox textBubbleRow(Message message, User currentUser) {
        boolean own = message.getSenderUsername().equalsIgnoreCase(currentUser.getUsername());
        VBox bubble = new VBox(2);
        bubble.getStyleClass().addAll("chat-bubble", own ? "chat-bubble-own" : "chat-bubble-other");
        bubble.setMaxWidth(520);

        Label sender = new Label(own ? "You" : message.getSender().getNameWithUsername());
        sender.getStyleClass().add("chat-sender");
        Label text = new Label(message.getContent());
        text.setWrapText(true);
        text.getStyleClass().add("chat-text");
        Label time = new Label(message.getSentTime().format(TIME_FORMAT));
        time.getStyleClass().add("chat-time");
        bubble.getChildren().addAll(sender, text, time);

        HBox row = new HBox(bubble);
        row.setAlignment(own ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    /** Renders a Formal Offer as one card, never as a text bubble. */
    private static HBox formalOfferRow(Message message, User currentUser,
                                       ChatService chatService, Runnable refresh,
                                       Consumer<DealRequest> onViewRequest) {
        DealRequest request = message.getDealRequest();
        boolean own = message.getSenderUsername().equalsIgnoreCase(currentUser.getUsername());

        Label kind = new Label("FORMAL OFFER");
        kind.getStyleClass().add("offer-badge");
        Label status = new Label(request.getStatus().name());
        status.getStyleClass().addAll("badge", statusStyle(request.getStatus()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, kind, spacer, status);
        top.setAlignment(Pos.CENTER_LEFT);

        Label terms = new Label(termsText(request));
        terms.setWrapText(true);
        terms.getStyleClass().add("offer-terms");

        Label listing = new Label(request.getListing().getTitle());
        listing.getStyleClass().add("offer-listing");

        Label meta = new Label((own ? "You" : message.getSender().getNameWithUsername())
                + " \u00b7 " + message.getSentTime().format(TIME_FORMAT));
        meta.getStyleClass().add("chat-time");

        VBox card = new VBox(6, top, terms, listing, meta);
        card.getStyleClass().add("offer-card");
        card.setMaxWidth(520);

        HBox actions = offerActions(chatService, currentUser, request, refresh);
        if (actions != null) {
            card.getChildren().add(actions);
        }

        if (showsRequestLink(currentUser, request) && onViewRequest != null) {
            Hyperlink viewRequest = new Hyperlink("View request");
            viewRequest.setOnAction(e -> onViewRequest.accept(request));
            card.getChildren().add(viewRequest);
        }

        HBox row = new HBox(card);
        row.setAlignment(own ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private static HBox offerActions(ChatService chatService, User currentUser,
                                     DealRequest request, Runnable refresh) {
        boolean isListingSeller = currentUser.getUsername()
                .equalsIgnoreCase(request.getListing().getSellerUsername());
        if (!isListingSeller || request.getStatus() != RequestStatus.PENDING) {
            return null;
        }
        Button accept = new Button("Accept");
        accept.getStyleClass().addAll("btn-primary", "btn-sm");
        accept.setOnAction(e -> submit(() -> chatService.acceptOffer(currentUser, request), refresh));

        Button decline = new Button("Decline");
        decline.getStyleClass().addAll("btn-sm");
        decline.setOnAction(e ->
                submit(() -> chatService.declineOffer(currentUser, request), refresh));

        HBox actions = new HBox(8, accept, decline);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return actions;
    }

    /**
     * True only for the listing's seller looking at a request that still needs an answer.
     * Null-safe so an absent viewer or request never renders the action.
     */
    static boolean showsRequestLink(User viewer, DealRequest request) {
        if (viewer == null || request == null) {
            return false;
        }
        boolean isListingSeller = viewer.getUsername() != null
                && viewer.getUsername().equalsIgnoreCase(request.getSellerUsername());
        return isListingSeller
                && (request.getStatus() == RequestStatus.PENDING
                    || request.getStatus() == RequestStatus.COUNTERED);
    }

    private static String termsText(DealRequest request) {
        if (request instanceof PriceOffer offer) {
            return "Price offer: " + new Money(offer.getOfferedPrice()).getDisplay();
        }
        if (request instanceof BorrowRequest borrow) {
            return "Borrow for " + DurationText.format(
                    Duration.between(borrow.getStartDate(), borrow.getEndDate()));
        }
        return "Claim for this giveaway";
    }

    private static String statusStyle(RequestStatus status) {
        return switch (status) {
            case PENDING -> "badge-pending";
            case ACCEPTED -> "badge-live";
            case DECLINED -> "badge-rejected";
            case COUNTERED -> "badge-sold";
        };
    }

    private static VBox searchPanel(TextField keyword, ComboBox<MessageSearchFilter> kind, Runnable refresh) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(10));

        keyword.setPromptText("Search this chat...");
        HBox.setHgrow(keyword, Priority.ALWAYS);
        kind.setValue(MessageSearchFilter.ALL);

        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");

        HBox controls = new HBox(8, keyword, kind, search);
        controls.setAlignment(Pos.CENTER_LEFT);

        search.setOnAction(e -> refresh.run());
        keyword.setOnAction(e -> refresh.run());
        kind.setOnAction(e -> refresh.run());

        panel.getChildren().add(controls);
        return panel;
    }

    /** E.g. "2 messages match 'lamp'" or "1 offer in this chat". */
    static String searchSummary(int count, String query, MessageSearchFilter kind) {
        String noun = switch (kind) {
            case OFFERS_ONLY -> count == 1 ? "offer" : "offers";
            case TEXT_ONLY -> count == 1 ? "text message" : "text messages";
            case ALL -> count == 1 ? "message" : "messages";
        };
        if (query.isEmpty()) {
            return count + " " + noun + " in this chat";
        }
        return count + " " + noun + (count == 1 ? " matches '" : " match '") + query + "'";
    }

    private static final String MAKE_OFFER_ID = "chat-make-offer";

    private static HBox composer(ChatService chatService, User currentUser, ChatThread thread,
                                 Runnable refresh) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField field = new TextField();
        field.setPromptText("Type a message...");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button send = new Button("Send");
        send.getStyleClass().addAll("btn-primary", "btn-sm");
        send.setOnAction(e -> {
            try {
                chatService.sendText(currentUser, thread, field.getText());
                field.clear();
                refresh.run();
            } catch (RuntimeException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            }
        });
        field.setOnAction(e -> send.fire());

        boolean isThreadBuyer = currentUser.getUsername()
                .equalsIgnoreCase(thread.getBuyer().getUsername());
        Button makeOffer = new Button("Make Offer");
        makeOffer.getStyleClass().addAll("btn-gold", "btn-sm");
        makeOffer.setId(MAKE_OFFER_ID);
        // The refresh re-checks acceptsOffers and hides this button once the listing leaves Live.
        makeOffer.setOnAction(e -> openFormalOfferForm(chatService, currentUser, thread, refresh));

        if (isThreadBuyer) {
            boolean open = chatService.acceptsOffers(thread);
            makeOffer.setVisible(open);
            makeOffer.setManaged(open);
            row.getChildren().add(makeOffer);
        }
        row.getChildren().addAll(field, send);
        return row;
    }

    private static void openFormalOfferForm(ChatService chatService, User currentUser,
                                            ChatThread thread, Runnable afterPost) {
        switch (thread.getListing().getType()) {
            case SELL -> promptPrice(chatService, currentUser, thread, afterPost);
            case LEND -> promptBorrowPeriod(chatService, currentUser, thread, afterPost);
            case GIVEAWAY -> confirmClaim(chatService, currentUser, thread, afterPost);
        }
    }

    private static void promptPrice(ChatService chatService, User currentUser, ChatThread thread,
                                    Runnable afterPost) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Make an Offer");
        dialog.setHeaderText("Formal price offer for: " + thread.getListing().getTitle());
        dialog.setContentText("Your offer price (HK$):");
        dialog.showAndWait().ifPresent(raw -> {
            BigDecimal price;
            try {
                price = new BigDecimal(raw.trim());
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Enter a valid price, e.g. 120 or 99.50")
                        .showAndWait();
                return;
            }
            submit(() -> chatService.submitFormalOffer(currentUser, thread, price), afterPost);
        });
    }

    private static void promptBorrowPeriod(ChatService chatService, User currentUser,
                                           ChatThread thread, Runnable afterPost) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Request to Borrow");
        dialog.setHeaderText("Formal borrow request for: " + thread.getListing().getTitle());

        TextField days = new TextField();
        days.setPromptText("0");
        TextField hours = new TextField();
        hours.setPromptText("0");
        TextField minutes = new TextField();
        minutes.setPromptText("0");
        TextField seconds = new TextField();
        seconds.setPromptText("0");
        HBox periodFields = new HBox(8,
                durationField("Days", days),
                durationField("Hours", hours),
                durationField("Minutes", minutes),
                durationField("Seconds", seconds));
        Label periodHint = new Label(
                "Enter days / hours / minutes / seconds (minimum total 5 seconds).");
        periodHint.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        VBox form = new VBox(8,
                new Label("Proposed lending period (days, hours, minutes, seconds)"),
                periodFields, periodHint);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);

        ButtonType send = new ButtonType("Send Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
        dialog.showAndWait().filter(send::equals).ifPresent(ignored -> {
            Duration period;
            try {
                period = parsePeriod(days, hours, minutes, seconds);
            } catch (RuntimeException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
                return;
            }
            LocalDateTime start = LocalDateTime.now().plusSeconds(1);
            LocalDateTime end = start.plus(period);
            submit(() -> chatService.submitFormalOffer(currentUser, thread, start, end), afterPost);
        });
    }

    private static void confirmClaim(ChatService chatService, User currentUser, ChatThread thread,
                                     Runnable afterPost) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Claim Item");
        confirm.setHeaderText("Claim: " + thread.getListing().getTitle());
        confirm.setContentText("Send a formal claim for this giveaway item?");
        confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored ->
                submit(() -> chatService.submitFormalOffer(currentUser, thread), afterPost));
    }

    private static VBox durationField(String unit, TextField field) {
        Label unitLabel = new Label(unit);
        unitLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, unitLabel, field);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static Duration parsePeriod(TextField daysField, TextField hoursField,
                                        TextField minutesField, TextField secondsField) {
        return parsePeriod(daysField.getText(), hoursField.getText(),
                minutesField.getText(), secondsField.getText());
    }

    /**
     * Parses a lending period in days, hours, minutes and seconds; blank parts count as zero.
     * Seconds allow the shortest allowed borrow period (5 seconds) to be requested.
     */
    public static Duration parsePeriod(String daysText, String hoursText,
                                       String minutesText, String secondsText) {
        String days = daysText.trim();
        String hours = hoursText.trim();
        String minutes = minutesText.trim();
        String seconds = secondsText.trim();
        if (days.isEmpty() && hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Enter a lending period in days, hours, minutes and/or seconds.");
        }
        long dayCount = parsePart(days, "Days");
        long hourCount = parsePart(hours, "Hours");
        long minuteCount = parsePart(minutes, "Minutes");
        long secondCount = parsePart(seconds, "Seconds");
        if (dayCount < 0 || hourCount < 0 || minuteCount < 0 || secondCount < 0) {
            throw new IllegalArgumentException("Days, hours, minutes and seconds must be non-negative.");
        }
        return Duration.ofDays(dayCount).plusHours(hourCount)
                .plusMinutes(minuteCount).plusSeconds(secondCount);
    }

    private static long parsePart(String text, String label) {
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static void submit(Runnable action, Runnable afterPost) {
        try {
            action.run();
            afterPost.run();
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
        }
    }
}
