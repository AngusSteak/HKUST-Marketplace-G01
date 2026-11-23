package com.hkust.marketplace;

import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.LoanTimer;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
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

    @Override
    public void start(Stage primaryStage) {
        loanTimer = new LoanTimer(DummyDataService.getInstance());
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

    public static void main(String[] args) {
        launch(args);
    }
}
