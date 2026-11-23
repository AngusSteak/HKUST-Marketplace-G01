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

/**
 * Builds the one chat view shared by the buyer and seller portals, so both sides see the
 * same conversation record, the same read-only banner and the same send behaviour.
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
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        root.getChildren().add(header(thread));

        Runnable[] refresh = new Runnable[1];

        boolean readOnly = chatService.isReadOnly(thread);
        Label banner = new Label(ChatService.READ_ONLY_BANNER);
        banner.getStyleClass().add("chat-banner");
        banner.setWrapText(true);
        banner.setVisible(readOnly);
        banner.setManaged(readOnly);
        root.getChildren().add(banner);

        root.getChildren().add(searchPanel(chatService, currentUser, () -> refresh[0].run()));

        VBox messageList = new VBox(8);
        messageList.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        HBox composerRow = composer(chatService, currentUser, thread, readOnly,
                () -> refresh[0].run());
        root.getChildren().add(composerRow);

        Runnable rerender = () -> {
            messageList.getChildren().clear();
            List<Message> history = chatService.history(thread);
            if (history.isEmpty()) {
                Label empty = new Label("No messages yet.");
                empty.getStyleClass().add("subtitle-label");
                messageList.getChildren().add(empty);
            } else {
                for (Message message : history) {
                    messageList.getChildren().add(
                            messageRow(message, currentUser, chatService, () -> refresh[0].run()));
                }
            }
            boolean nowReadOnly = chatService.isReadOnly(thread);
            banner.setVisible(nowReadOnly);
            banner.setManaged(nowReadOnly);
            composerRow.setDisable(nowReadOnly);
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
        Label participants = new Label("Buyer: " + thread.getBuyer().getFullName()
                + "   \u00b7   Seller: " + thread.getSeller().getFullName()
                + "   \u00b7   " + thread.getListing().getStatus().name().replace('_', ' '));
        participants.getStyleClass().add("subtitle-label");
        header.getChildren().addAll(title, participants);
        return header;
    }

    private static HBox messageRow(Message message, User currentUser,
                                   ChatService chatService, Runnable refresh) {
        return message.isFormalOffer()
                ? formalOfferRow(message, currentUser, chatService, refresh)
                : textBubbleRow(message, currentUser);
    }

    private static HBox textBubbleRow(Message message, User currentUser) {
        boolean own = message.getSenderUsername().equalsIgnoreCase(currentUser.getUsername());
        VBox bubble = new VBox(2);
        bubble.getStyleClass().addAll("chat-bubble", own ? "chat-bubble-own" : "chat-bubble-other");
        bubble.setMaxWidth(520);

        Label sender = new Label(own ? "You" : message.getSender().getFullName());
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
                                       ChatService chatService, Runnable refresh) {
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

        Label meta = new Label((own ? "You" : message.getSender().getFullName())
                + " \u00b7 " + message.getSentTime().format(TIME_FORMAT));
        meta.getStyleClass().add("chat-time");

        VBox card = new VBox(6, top, terms, listing, meta);
        card.getStyleClass().add("offer-card");
        card.setMaxWidth(520);

        HBox actions = offerActions(chatService, currentUser, request, refresh);
        if (actions != null) {
            card.getChildren().add(actions);
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

    private static VBox searchPanel(ChatService chatService, User currentUser, Runnable refresh) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(10));

        TextField keyword = new TextField();
        keyword.setPromptText("Search all my chats...");
        HBox.setHgrow(keyword, Priority.ALWAYS);

        ComboBox<MessageSearchFilter> filter = new ComboBox<>(
                FXCollections.observableArrayList(MessageSearchFilter.values()));
        filter.setValue(MessageSearchFilter.ALL);

        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");

        HBox controls = new HBox(8, keyword, filter, search);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox results = new VBox(8);
        ScrollPane resultsScroll = new ScrollPane(results);
        resultsScroll.setFitToWidth(true);
        resultsScroll.setPrefViewportHeight(200);
        resultsScroll.setVisible(false);
        resultsScroll.setManaged(false);

        Runnable[] searchAgain = new Runnable[1];
        Runnable runSearch = () -> {
            results.getChildren().clear();
            List<Message> matches =
                    chatService.search(currentUser, keyword.getText(), filter.getValue());
            resultsScroll.setVisible(true);
            resultsScroll.setManaged(true);
            if (matches.isEmpty()) {
                Label empty = new Label("No matching messages.");
                empty.getStyleClass().add("subtitle-label");
                results.getChildren().add(empty);
            } else {
                for (Message message : matches) {
                    results.getChildren().add(messageRow(message, currentUser, chatService,
                            () -> {
                                refresh.run();
                                searchAgain[0].run();
                            }));
                }
            }
        };
        searchAgain[0] = runSearch;

        search.setOnAction(e -> runSearch.run());
        keyword.setOnAction(e -> runSearch.run());
        filter.setOnAction(e -> runSearch.run());

        panel.getChildren().addAll(controls, resultsScroll);
        return panel;
    }

    private static HBox composer(ChatService chatService, User currentUser, ChatThread thread,
                                 boolean readOnly, Runnable refresh) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField field = new TextField();
        field.setPromptText(readOnly ? "Chat is read-only" : "Type a message...");
        field.setDisable(readOnly);
        HBox.setHgrow(field, Priority.ALWAYS);

        Button send = new Button("Send");
        send.getStyleClass().addAll("btn-primary", "btn-sm");
        send.setDisable(readOnly);
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
        Runnable afterPost = () -> {
            refresh.run();
            if (chatService.isReadOnly(thread)) {
                makeOffer.setDisable(true);
                field.setDisable(true);
                send.setDisable(true);
            }
        };
        makeOffer.setOnAction(e -> openFormalOfferForm(chatService, currentUser, thread, afterPost));

        if (isThreadBuyer && !readOnly) {
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
        days.setPromptText("Days");
        TextField hours = new TextField();
        hours.setPromptText("Hours (0\u201323)");
        VBox form = new VBox(8, new Label("Proposed lending period"), days, hours);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);

        ButtonType send = new ButtonType("Send Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
        dialog.showAndWait().filter(send::equals).ifPresent(ignored -> {
            Duration period;
            try {
                period = parsePeriod(days, hours);
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

    private static Duration parsePeriod(TextField daysField, TextField hoursField) {
        String daysText = daysField.getText().trim();
        String hoursText = hoursField.getText().trim();
        if (daysText.isEmpty() && hoursText.isEmpty()) {
            throw new IllegalArgumentException("Enter a lending period in days and/or hours.");
        }
        long days = daysText.isEmpty() ? 0 : Long.parseLong(daysText);
        long hours = hoursText.isEmpty() ? 0 : Long.parseLong(hoursText);
        if (days < 0 || hours < 0) {
            throw new IllegalArgumentException("Days and hours must be non-negative.");
        }
        return Duration.ofDays(days).plusHours(hours);
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
