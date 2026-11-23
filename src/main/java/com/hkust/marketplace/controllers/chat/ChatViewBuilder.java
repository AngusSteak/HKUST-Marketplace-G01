package com.hkust.marketplace.controllers.chat;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.CounterOffer;
import com.hkust.marketplace.models.CounterStatus;
import com.hkust.marketplace.models.DealRequest;
import com.hkust.marketplace.models.ListingStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
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

        root.getChildren().add(searchPanel(chatService, currentUser, () -> refresh[0].run()));

        VBox messageList = new VBox(8);
        messageList.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        HBox composerRow = composer(chatService, currentUser, thread, () -> refresh[0].run());
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
        Label participants = new Label("Buyer: " + thread.getBuyer().getFullName()
                + "   \u00b7   Seller: " + thread.getSeller().getFullName()
                + "   \u00b7   " + thread.getListing().getStatus().name().replace('_', ' '));
        participants.getStyleClass().add("subtitle-label");
        header.getChildren().addAll(title, participants);
        return header;
    }

    private static HBox messageRow(Message message, User currentUser,
                                   ChatService chatService, Runnable refresh) {
        if (message.isCounterOffer()) {
            return counterOfferRow(message, currentUser, chatService, refresh);
        }
        if (message.isFormalOffer()) {
            return formalOfferRow(message, currentUser, chatService, refresh);
        }
        return textBubbleRow(message, currentUser);
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

    /** Renders a buyer's Formal Offer as one card, never as a text bubble. */
    private static HBox formalOfferRow(Message message, User currentUser,
                                       ChatService chatService, Runnable refresh) {
        DealRequest request = message.getDealRequest();
        return offerCardRow(message, currentUser, "FORMAL OFFER",
                request.getStatus().name(), statusStyle(request.getStatus()),
                termsText(request), offerActions(chatService, currentUser, request, refresh));
    }

    /** Renders a seller's counter as its own card, never as a text bubble. */
    private static HBox counterOfferRow(Message message, User currentUser,
                                        ChatService chatService, Runnable refresh) {
        CounterOffer counter = message.getCounterOffer();
        return offerCardRow(message, currentUser, "COUNTER OFFER",
                counter.getStatus().name(), counterStatusStyle(counter.getStatus()),
                counterTerms(counter), counterActions(chatService, currentUser, counter, refresh));
    }

    /** The one card layout shared by a buyer's offer and a seller's counter. */
    private static HBox offerCardRow(Message message, User currentUser, String badgeText,
                                     String statusText, String statusStyle, String terms,
                                     HBox actions) {
        boolean own = message.getSenderUsername().equalsIgnoreCase(currentUser.getUsername());
        DealRequest request = message.isCounterOffer()
                ? message.getCounterOffer().getRequest()
                : message.getDealRequest();

        Label kind = new Label(badgeText);
        kind.getStyleClass().add("offer-badge");
        Label status = new Label(statusText);
        status.getStyleClass().addAll("badge", statusStyle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, kind, spacer, status);
        top.setAlignment(Pos.CENTER_LEFT);

        Label termsLabel = new Label(terms);
        termsLabel.setWrapText(true);
        termsLabel.getStyleClass().add("offer-terms");

        Label listing = new Label(request.getListing().getTitle());
        listing.getStyleClass().add("offer-listing");

        Label meta = new Label((own ? "You" : message.getSender().getFullName())
                + " \u00b7 " + message.getSentTime().format(TIME_FORMAT));
        meta.getStyleClass().add("chat-time");

        VBox card = new VBox(6, top, termsLabel, listing, meta);
        card.getStyleClass().add("offer-card");
        card.setMaxWidth(520);

        if (actions != null) {
            card.getChildren().add(actions);
        }

        HBox row = new HBox(card);
        row.setAlignment(own ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Actions on the buyer's request card. While a counter is unanswered the request card offers
     * nothing: the seller cancels that counter first, then accepts, declines or counters again.
     */
    private static HBox offerActions(ChatService chatService, User currentUser,
                                     DealRequest request, Runnable refresh) {
        boolean isListingSeller = currentUser.getUsername()
                .equalsIgnoreCase(request.getListing().getSellerUsername());
        if (!isListingSeller || request.getStatus() != RequestStatus.PENDING) {
            return null;
        }
        List<Button> buttons = new ArrayList<>();

        Button accept = new Button("Accept");
        accept.getStyleClass().addAll("btn-primary", "btn-sm");
        accept.setOnAction(e -> submit(
                () -> chatService.acceptOffer(currentUser, request), refresh));
        buttons.add(accept);

        if (isCounterable(request) && request.getListing().getStatus() == ListingStatus.LIVE) {
            Button counter = new Button("Counter");
            counter.getStyleClass().addAll("btn-gold", "btn-sm");
            counter.setOnAction(e ->
                    openCounterForm(chatService, currentUser, request, refresh));
            buttons.add(counter);
        }

        Button decline = new Button("Decline");
        decline.getStyleClass().addAll("btn-sm");
        decline.setOnAction(e -> submit(
                () -> chatService.declineOffer(currentUser, request), refresh));
        buttons.add(decline);

        return actionsRow(buttons);
    }

    /**
     * Actions on a seller's counter card: only the buyer may accept or decline it, and only the
     * seller may cancel it, while it is still offered.
     */
    private static HBox counterActions(ChatService chatService, User currentUser,
                                       CounterOffer counter, Runnable refresh) {
        if (counter.getStatus() != CounterStatus.OFFERED) {
            return null;
        }
        List<Button> buttons = new ArrayList<>();
        boolean isListingSeller = currentUser.getUsername()
                .equalsIgnoreCase(counter.getSellerUsername());
        boolean isRequestBuyer = currentUser.getUsername()
                .equalsIgnoreCase(counter.getBuyerUsername());

        if (isRequestBuyer) {
            // Accepting reserves/lends the item, so it follows the same live rule as offers.
            if (counter.getListing().getStatus() == ListingStatus.LIVE) {
                Button accept = new Button("Accept Counter");
                accept.getStyleClass().addAll("btn-primary", "btn-sm");
                accept.setOnAction(e -> submit(
                        () -> chatService.acceptCounter(currentUser, counter), refresh));
                buttons.add(accept);
            }
            Button decline = new Button("Decline Counter");
            decline.getStyleClass().addAll("btn-sm");
            decline.setOnAction(e -> submit(
                    () -> chatService.declineCounter(currentUser, counter), refresh));
            buttons.add(decline);
        } else if (isListingSeller) {
            Button cancel = new Button("Cancel Counter");
            cancel.getStyleClass().addAll("btn-sm");
            cancel.setOnAction(e -> submit(
                    () -> chatService.cancelCounter(currentUser, counter), refresh));
            buttons.add(cancel);
        }
        return actionsRow(buttons);
    }

    private static HBox actionsRow(List<Button> buttons) {
        if (buttons.isEmpty()) {
            return null;
        }
        HBox actions = new HBox(8, buttons.toArray(Button[]::new));
        actions.setAlignment(Pos.CENTER_RIGHT);
        return actions;
    }

    /** Only Sale and Lend requests can be countered; a Giveaway claim cannot. */
    private static boolean isCounterable(DealRequest request) {
        return request instanceof PriceOffer || request instanceof BorrowRequest;
    }

    private static void openCounterForm(ChatService chatService, User currentUser,
                                        DealRequest request, Runnable afterPost) {
        String header = "Counter the offer for: " + request.getListing().getTitle();
        if (request instanceof PriceOffer) {
            promptPrice("Counter Offer", header, "Your counter price (HK$):",
                    price -> chatService.counterOffer(currentUser, request, price), afterPost);
        } else if (request instanceof BorrowRequest) {
            promptBorrowPeriod("Counter Offer", header, "Send Counter",
                    (start, end) -> chatService.counterOffer(currentUser, request, start, end),
                    afterPost);
        }
    }

    /** The buyer's original terms; a seller counter is shown on its own card, never here. */
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

    private static String counterTerms(CounterOffer counter) {
        if (counter.getPrice() != null) {
            return "Counter price: " + new Money(counter.getPrice()).getDisplay();
        }
        return "Counter period: " + DurationText.format(
                Duration.between(counter.getStartDate(), counter.getEndDate()));
    }

    private static String statusStyle(RequestStatus status) {
        return switch (status) {
            case PENDING -> "badge-pending";
            case ACCEPTED -> "badge-live";
            case DECLINED -> "badge-rejected";
            case COUNTERED -> "badge-sold";
        };
    }

    private static String counterStatusStyle(CounterStatus status) {
        return switch (status) {
            case OFFERED -> "badge-pending";
            case ACCEPTED -> "badge-live";
            case DECLINED, CANCELLED -> "badge-rejected";
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
        promptPrice("Make an Offer",
                "Formal price offer for: " + thread.getListing().getTitle(),
                "Your offer price (HK$):",
                price -> chatService.submitFormalOffer(currentUser, thread, price), afterPost);
    }

    /** One price dialog shared by the buyer's offer and the seller's counter. */
    private static void promptPrice(String title, String header, String prompt,
                                    Consumer<BigDecimal> action, Runnable afterPost) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(prompt);
        dialog.showAndWait().ifPresent(raw -> {
            BigDecimal price;
            try {
                price = new BigDecimal(raw.trim());
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Enter a valid price, e.g. 120 or 99.50")
                        .showAndWait();
                return;
            }
            submit(() -> action.accept(price), afterPost);
        });
    }

    private static void promptBorrowPeriod(ChatService chatService, User currentUser,
                                           ChatThread thread, Runnable afterPost) {
        promptBorrowPeriod("Request to Borrow",
                "Formal borrow request for: " + thread.getListing().getTitle(), "Send Request",
                (start, end) -> chatService.submitFormalOffer(currentUser, thread, start, end),
                afterPost);
    }

    /** One period dialog shared by the buyer's request and the seller's counter. */
    private static void promptBorrowPeriod(String title, String header, String sendLabel,
                                           BiConsumer<LocalDateTime, LocalDateTime> action,
                                           Runnable afterPost) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        TextField days = new TextField();
        days.setPromptText("Days");
        TextField hours = new TextField();
        hours.setPromptText("Hours (0\u201323)");
        VBox form = new VBox(8, new Label("Proposed lending period"), days, hours);
        form.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(form);

        ButtonType send = new ButtonType(sendLabel, ButtonBar.ButtonData.OK_DONE);
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
            submit(() -> action.accept(start, end), afterPost);
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
