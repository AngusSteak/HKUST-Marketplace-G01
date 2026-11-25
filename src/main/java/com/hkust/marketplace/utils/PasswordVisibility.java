package com.hkust.marketplace.utils;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Adds a show/hide toggle button beside a {@link PasswordField}.
 *
 * <p>JavaFX's {@code PasswordField} always masks its input and exposes no echo
 * character, so revealing the password is done by swapping it in place for a
 * {@link TextField} whose text is bidirectionally bound to the password field.
 * The two controls therefore always hold the same text, which means callers can
 * keep reading {@code passwordField.getText()} even while the password is shown.
 * The button keeps focus on the visible field so pressing Enter still submits
 * the form.</p>
 */
public final class PasswordVisibility {

    private PasswordVisibility() {
    }

    /**
     * Attaches a password visibility toggle button to the given field inside {@code row}.
     *
     * @param field the password field to control
     * @param row   the HBox that holds the field; the button is appended to it
     */
    public static void attach(PasswordField field, HBox row) {
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField reveal = new TextField(field.getText());
        reveal.setPromptText(field.getPromptText());
        reveal.setPrefHeight(field.getPrefHeight());
        reveal.setMaxWidth(Double.MAX_VALUE);
        reveal.setStyle(field.getStyle());
        reveal.getStyleClass().setAll(field.getStyleClass());
        HBox.setHgrow(reveal, Priority.ALWAYS);
        reveal.textProperty().bindBidirectional(field.textProperty());
        reveal.setVisible(false);
        reveal.setManaged(false);

        Button button = new Button("👁");
        button.getStyleClass().add("password-toggle");
        button.setFocusTraversable(false);
        Tooltip tooltip = new Tooltip("Show password");
        button.setTooltip(tooltip);

        final boolean[] revealed = { false };
        button.setOnAction(e -> {
            revealed[0] = !revealed[0];
            TextInputControl from = revealed[0] ? field : reveal;
            TextInputControl to = revealed[0] ? reveal : field;

            int index = row.getChildren().indexOf(from);
            if (index >= 0) {
                row.getChildren().set(index, to);
            }
            to.setVisible(true);
            to.setManaged(true);
            from.setVisible(false);
            from.setManaged(false);

            to.requestFocus();
            String text = to.getText();
            to.positionCaret(text == null ? 0 : text.length());
            tooltip.setText(revealed[0] ? "Hide password" : "Show password");
        });

        row.getChildren().add(button);
    }
}
