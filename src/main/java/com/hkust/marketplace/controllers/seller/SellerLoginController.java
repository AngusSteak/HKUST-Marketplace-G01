package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.LoginGate;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class SellerLoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }
        User user = DummyDataService.getInstance().users().find(username);
        switch (LoginGate.check(user, UserRole.SELLER, password)) {
            case USER_NOT_FOUND -> showError("User not found.");
            case WRONG_PORTAL -> showError("This account is not a Seller account. Use the correct portal.");
            case WRONG_PASSWORD -> showError("Incorrect password.");
            case DELETED -> showError("This account has been deleted and cannot log in.");
            case DISABLED -> showError("Account deactivated.");
            case OK -> {
                SessionManager.login(user);
                SceneManager.showSellerDashboard();
            }
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    @FXML private void goRegister() { SceneManager.showSellerRegister(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
