package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.PasswordRule;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.RegistrationService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.UserStore;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Set;

/** Handles the buyer registration form, including the UC-1.1 password rules. */
public class BuyerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> residenceCombo;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    /** Fills the residence-hall choices. */
    @FXML
    public void initialize() {
        residenceCombo.setItems(FXCollections.observableArrayList(
                "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
                "Hall 6", "Hall 7", "Hall 8", "Hall 9",
                "UG Hall 1", "UG Hall 2", "Off-campus"
        ));
    }

    @FXML
    private void handleRegister() {
        hideMessages();

        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String residence = residenceCombo.getValue();

        // Validation
        if (username.isEmpty()) {
            showError("Username is required.");
            return;
        }
        if (DummyDataService.getInstance().users().usernameExists(username)) {
            showError("Username already taken. Please choose another.");
            return;
        }
        if (fullName.isEmpty()) {
            showError("Full Name cannot be empty.");
            return;
        }
        Set<PasswordRule> failed = UserStore.checkPasswordStrength(password, username);
        if (!failed.isEmpty()) {
            showError(UserStore.formatPasswordErrors(failed));
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        Buyer user = new Buyer(username.toLowerCase(), fullName, PasswordHasher.hash(password), UserRole.BUYER);
        if (residence != null) {
            user.setResidenceHall(residence);
        }
        // Saves the account and notifies the admin (SRS UC-1.1 step 7).
        if (!new RegistrationService(DummyDataService.getInstance()).register(user)) {
            showError("Username already taken. Please choose another.");
            return;
        }

        String message = "Registration successful! You can now login.";
        showSuccess(message);
        // Clear form
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        residenceCombo.getSelectionModel().clearSelection();
        showInfo("Registration successful", message);
        SceneManager.showBuyerLogin();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showSuccess(String msg) {
        successLabel.setText(msg);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }

    private void hideMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML private void goLogin() { SceneManager.showBuyerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
