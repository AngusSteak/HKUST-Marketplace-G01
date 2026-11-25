package com.hkust.marketplace;

import com.hkust.marketplace.persistence.DatabaseInUseException;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.LoanTimer;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * HKUST Marketplace - Main Application Entry Point
 * Frontend-only demo / skeleton for student development.
 *
 * This application provides a complete UI skeleton for the Buyer, Seller and Admin portals.
 * Data is stored in a local SQLite database (data/marketplace.db); see DummyDataService.
 */
public class MarketplaceApp extends Application {

    /** Auto-returns overdue loans and sends due-date reminders while the app runs (#22). */
    private LoanTimer loanTimer;

    /** Starts the JavaFX window and the loan timer. */
    @Override
    public void start(Stage primaryStage) {
        try {
            loanTimer = new LoanTimer(DummyDataService.getInstance());
        } catch (DatabaseInUseException e) {
            // A second copy would not see the first one's changes and could overwrite them.
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "HKUST Marketplace is already running on this computer.\n\n"
                            + "Use the window that is already open, or close it and start the app again. "
                            + "To switch user, log out and log in again.");
            alert.setHeaderText("Already running");
            alert.setTitle("HKUST Marketplace");
            alert.showAndWait();
            Platform.exit();
            return;
        } catch (RuntimeException e) {
            // e.g. a read-only data/ folder or a file system without locking
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "The database in the data/ folder could not be opened:\n" + e.getMessage()
                            + "\n\nCheck that the project folder is writable, then start the app again.");
            alert.setHeaderText("Could not open the database");
            alert.setTitle("HKUST Marketplace");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        loanTimer.start();
        SceneManager.init(primaryStage);
        SceneManager.showWelcome();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    /** Closes the SQLite database cleanly when the window is closed. */
    @Override
    public void stop() {
        if (loanTimer != null) {
            loanTimer.stop();
        }
        DummyDataService.shutdown();
    }

    /** Launches the application. */
    public static void main(String[] args) {
        launch(args);
    }
}
