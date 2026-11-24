package com.hkust.marketplace.controllers.notifications;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.utils.DummyDataService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the notification board shared by the buyer, seller and admin portals. The view owns no
 * state: the caller keeps the current filter (so it survives navigation) and supplies the two
 * callbacks for filter changes and for opening a notification.
 */
public final class NotificationBoardView {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private NotificationBoardView() {
    }

    /** One choice in the category filter; a {@code null} value means "All categories". */
    private record Option<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Builds the board for {@code username}. The first filter item is "All categories" (a
     * {@code null} category), followed by each supplied category's label in the given order.
     */
    public static Parent build(String username, List<NotificationCategory> categories,
                               NotificationCategory filter,
                               Consumer<NotificationCategory> onFilterChange,
                               Consumer<Notification> onOpen) {
        DummyDataService data = DummyDataService.getInstance();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        if (data.notifications().forUser(username).isEmpty()) {
            // SRS UC-1.8 A1: nothing but the empty-state message.
            root.getChildren().add(muted("You have no notifications."));
            return wrap(root);
        }

        ComboBox<Option<NotificationCategory>> filterBox = new ComboBox<>();
        filterBox.getItems().add(new Option<>("All categories", null));
        for (NotificationCategory category : categories) {
            filterBox.getItems().add(new Option<>(category.getLabel(), category));
        }
        // Preselect before wiring the handler so the initial selection never fires onFilterChange.
        filterBox.getSelectionModel().selectFirst();
        filterBox.getItems().stream()
                .filter(o -> o.value() == filter)
                .findFirst()
                .ifPresent(o -> filterBox.getSelectionModel().select(o));
        filterBox.setOnAction(e -> {
            Option<NotificationCategory> chosen = filterBox.getValue();
            onFilterChange.accept(chosen == null ? null : chosen.value());
        });

        long unread = data.notifications().unreadCount(username);
        Label unreadLabel = muted(unread == 0 ? "All read" : unread + " unread");
        HBox header = new HBox(12, new Label("Category:"), filterBox, unreadLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(header);

        List<Notification> notifs = data.notifications().forUser(username, filter);
        if (notifs.isEmpty()) {
            root.getChildren().add(muted("No notifications in this category."));
        }
        for (Notification n : notifs) {
            root.getChildren().add(row(n, onOpen));
        }

        return wrap(root);
    }

    private static HBox row(Notification n, Consumer<Notification> onOpen) {
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
                + n.getTimestamp().format(DATE_TIME));
        meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        VBox text = new VBox(4, titleRow, msg, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button open = new Button(n.getRelatedRef() == null ? "Mark read" : "Open");
        open.getStyleClass().addAll("btn-outline", "btn-sm");
        open.setDisable(n.getRelatedRef() == null && n.isRead());
        open.setOnAction(e -> onOpen.accept(n));

        HBox item = new HBox(12, text, open);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("notif-item");
        return item;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        label.setWrapText(true);
        return label;
    }

    private static ScrollPane wrap(VBox root) {
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }
}
