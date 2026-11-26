package com.hkust.marketplace.controllers;

import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The welcome, login and registration screens (SRS UC-1.1, UC-1.2, UC-2.1, UC-2.2, UC-3.1). */
class LoginAndRegisterScreensTest extends FxTest {

    @Test
    void theWelcomeScreenLeadsToEachPortal() {
        open(SceneManager::showWelcome);
        click("Enter as Buyer");
        assertTrue(screenText().contains("Buyer Login"));
        click("← Back to Welcome");
        click("Enter as Seller");
        assertTrue(screenText().contains("Seller Login"));
        click("← Back to Welcome");
        click("Enter as Admin");
        assertTrue(screenText().contains("Admin Login"));
        click("← Back to Welcome");
    }

    @Test
    void buyerLoginExplainsEachRefusalThenSignsIn() {
        open(SceneManager::showBuyerLogin);
        assertLoginError("", "", "Please enter both username and password.");
        assertLoginError("nobody", "password1", "User not found");
        assertLoginError("charlie", "password1", "not a Buyer account");
        assertLoginError("alice", "wrong", "Incorrect password");
        data.users().find("bob").setActive(false);
        assertLoginError("bob", "password1", "deactivated");
        data.users().find("bob").setActive(true);
        data.users().find("bob").setDeleted(true);
        assertLoginError("bob", "password1", "deleted");

        type("Enter username", "alice");
        type("Enter password", "password1");
        click("Show");
        click("Login");
        assertNotNull(SessionManager.getCurrentUser());
        assertEquals(UserRole.BUYER, SessionManager.getCurrentUser().getRole());
    }

    @Test
    void sellerLoginExplainsEachRefusalThenSignsIn() {
        open(SceneManager::showSellerLogin);
        assertLoginError("", "", "");
        assertLoginError("nobody", "password1", "");
        assertLoginError("alice", "password1", "");
        assertLoginError("charlie", "wrong", "");
        data.users().find("diana").setActive(false);
        assertLoginError("diana", "password1", "");
        data.users().find("diana").setDeleted(true);
        assertLoginError("diana", "password1", "");

        type("Enter username", "charlie");
        type("Enter password", "password1");
        click("Login");
        assertEquals(UserRole.SELLER, SessionManager.getCurrentUser().getRole());
        click("Logout");
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void adminLoginExplainsEachRefusalThenSignsIn() {
        open(SceneManager::showAdminLogin);
        assertAdminError("", "");
        assertAdminError("alice", "password1");
        assertAdminError("admin", "wrong");
        type("admin", "admin");
        type("Enter password", "admin123");
        click("Login as Admin");
        assertEquals(UserRole.ADMIN, SessionManager.getCurrentUser().getRole());
    }

    @Test
    void buyerRegistrationChecksEachRuleThenCreatesTheAccount() {
        open(SceneManager::showBuyerRegister);
        assertRegisterError("Create Account", "", "New Name", "Password1!", "Password1!", "Username is required");
        assertRegisterError("Create Account", "alice", "New Name", "Password1!", "Password1!", "already taken");
        assertRegisterError("Create Account", "newbie", "", "Password1!", "Password1!", "Full Name");
        assertRegisterError("Create Account", "newbie", "New Name", "weak", "weak", "");
        assertRegisterError("Create Account", "newbie", "New Name", "Password1!", "Password2!", "do not match");

        fill("newbie", "New Name", "Password1!", "Password1!");
        choose(0, 2);
        click("Create Account");
        assertNotNull(data.users().find("newbie"));
        assertFalse(dialogsSeen.isEmpty(), "a success message was shown");

        open(SceneManager::showBuyerRegister);
        click("Login here");
        assertTrue(screenText().contains("Buyer Login"));
    }

    @Test
    void sellerRegistrationChecksEachRuleThenCreatesTheAccount() {
        open(SceneManager::showSellerRegister);
        assertRegisterError("Create Seller Account", "", "New Seller", "Password1!", "Password1!", "");
        assertRegisterError("Create Seller Account", "charlie", "New Seller", "Password1!", "Password1!", "");
        assertRegisterError("Create Seller Account", "seller2", "", "Password1!", "Password1!", "");
        assertRegisterError("Create Seller Account", "seller2", "New Seller", "short", "short", "");
        assertRegisterError("Create Seller Account", "seller2", "New Seller", "Password1!", "Other1!!", "");

        fill("seller2", "New Seller", "Password1!", "Password1!");
        type("Short introduction", "I sell books");
        fx(() -> all(javafx.scene.control.CheckBox.class).forEach(box -> box.setSelected(true)));
        click("Create Seller Account");
        assertNotNull(data.users().find("seller2"));

        open(SceneManager::showSellerRegister);
        click("Login here");
        open(SceneManager::showSellerRegister);
        click("← Back to Welcome");
    }

    private void assertLoginError(String user, String password, String expected) {
        type("Enter username", user);
        type("Enter password", password);
        click("Login");
        assertNull(SessionManager.getCurrentUser());
        assertTrue(screenText().contains(expected), "expected '" + expected + "'");
    }

    private void assertAdminError(String user, String password) {
        type("admin", user);
        type("Enter password", password);
        click("Login as Admin");
        assertNull(SessionManager.getCurrentUser());
    }

    private void assertRegisterError(String button, String user, String name, String password, String confirm,
                                     String expected) {
        fill(user, name, password, confirm);
        int before = data.users().all().size();
        click(button);
        assertEquals(before, data.users().all().size());
        assertTrue(screenText().contains(expected), "expected '" + expected + "'");
    }

    private void fill(String user, String name, String password, String confirm) {
        type("Unique username", user);
        type("Your full name", name);
        type("Min 8 chars", password);
        type("Re-enter password", confirm);
    }
}
