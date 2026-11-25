package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.PasswordRule;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.RegistrationService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.UserStore;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/** Handles the seller registration form, including the UC-1.1 password rules. */
public class SellerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private GridPane meetupGrid;
    @FXML private TextArea bioArea;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private final EnumMap<Location, CheckBox> meetupBoxes = new EnumMap<>(Location.class);

    /** Fills the multi-select meet-up location choices. */
    @FXML
    public void initialize() {
        int i = 0;
        for (Location location : Location.values()) {
            CheckBox box = new CheckBox(location.getLabel());
            meetupGrid.add(box, i % 2, i / 2);
            meetupBoxes.put(location, box);
            i++;
        }
    }

    @FXML
    private void handleRegister() {
        hideMessages();
        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (username.isEmpty()) { showError("Username is required."); return; }
        if (DummyDataService.getInstance().users().usernameExists(username)) {
            showError("Username already taken."); return;
        }
        if (fullName.isEmpty()) { showError("Full Name cannot be empty."); return; }
        Set<PasswordRule> failed = UserStore.checkPasswordStrength(password, username);
        if (!failed.isEmpty()) { showError(UserStore.formatPasswordErrors(failed)); return; }
        if (!password.equals(confirm)) { showError("Passwords do not match."); return; }

        Seller user = new Seller(username.toLowerCase(), fullName, PasswordHasher.hash(password), UserRole.SELLER);
        EnumSet<Location> meetups = EnumSet.noneOf(Location.class);
        meetupBoxes.forEach((location, box) -> {
            if (box.isSelected()) meetups.add(location);
        });
        user.setPreferredMeetup(meetups);
        if (bioArea.getText() != null && !bioArea.getText().isBlank()) user.setBio(bioArea.getText().trim());
        // Saves the account and notifies the admin (SRS UC-2.1, like UC-1.1 step 7).
        if (!new RegistrationService(DummyDataService.getInstance()).register(user)) {
            showError("Username already taken."); return;
        }

        String message = "Seller account created! You can now login.";
        showSuccess(message);
        usernameField.clear(); fullNameField.clear(); passwordField.clear();
        confirmPasswordField.clear(); meetupBoxes.values().forEach(box -> box.setSelected(false)); bioArea.clear();
        showInfo("Seller account created", message);
        SceneManager.showSellerLogin();
    }

    private void showError(String msg) {
        errorLabel.setText(msg); errorLabel.setVisible(true); errorLabel.setManaged(true);
    }
    private void showSuccess(String msg) {
        successLabel.setText(msg); successLabel.setVisible(true); successLabel.setManaged(true);
    }
    private void hideMessages() {
        errorLabel.setVisible(false); errorLabel.setManaged(false);
        successLabel.setVisible(false); successLabel.setManaged(false);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML private void goLogin() { SceneManager.showSellerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
