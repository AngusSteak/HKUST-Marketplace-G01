package com.hkust.marketplace.controllers;

import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;

/** Landing screen where the visitor picks the Buyer, Seller or Admin portal. */
public class WelcomeController {

    @FXML
    private void goBuyer() {
        System.out.println("[Welcome] goBuyer()");
        SceneManager.showBuyerLogin();
    }

    @FXML
    private void goSeller() {
        System.out.println("[Welcome] goSeller()");
        SceneManager.showSellerLogin();
    }

    @FXML
    private void goAdmin() {
        System.out.println("[Welcome] goAdmin()");
        SceneManager.showAdminLogin();
    }
}
