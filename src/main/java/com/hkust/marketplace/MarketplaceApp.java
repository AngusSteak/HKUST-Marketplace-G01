package com.hkust.marketplace;

import com.hkust.marketplace.utils.DummyDataService;
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

    @Override
    public void start(Stage primaryStage) {
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
        DummyDataService.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
