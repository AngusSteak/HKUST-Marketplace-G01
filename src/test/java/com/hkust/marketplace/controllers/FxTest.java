package com.hkust.marketplace.controllers;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import com.hkust.marketplace.utils.TestData;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Base class for screen tests: starts JavaFX once (the tests are skipped on a machine without a
 * display), gives every test the demo data on a private in-memory database (never
 * {@code data/marketplace.db}), and runs actions on the JavaFX thread.
 *
 * <p>Pop-up dialogs would block a test, so while an action runs any open dialog is answered by
 * {@link #onDialog}: by default it presses the dialog's Cancel/Close button, or OK when there is
 * nothing else.
 */
public abstract class FxTest {

    private static Boolean toolkitStarted;

    /** The demo data the screens see in this test. */
    protected DummyDataService data;
    /** The window the screens are shown in. */
    protected Stage stage;
    /** How each dialog is answered: return the kind of button to press. */
    protected Function<DialogPane, ButtonBar.ButtonData> onDialog = pane -> ButtonBar.ButtonData.CANCEL_CLOSE;
    /** The header and content text of every dialog answered so far in this test. */
    protected final List<String> dialogsSeen = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startScreens() {
        Assumptions.assumeTrue(startToolkit(), "JavaFX needs a display; screen tests skipped");
        data = TestData.installSeeded();
        fx(() -> {
            stage = new Stage();
            SceneManager.init(stage);
            stage.setWidth(1200);
            stage.setHeight(780);
        });
    }

    @AfterEach
    void stopScreens() {
        if (stage != null) {
            fx(() -> stage.close());
        }
        SessionManager.logout();
        TestData.uninstall();
    }

    private static synchronized boolean startToolkit() {
        if (toolkitStarted == null) {
            try {
                CountDownLatch ready = new CountDownLatch(1);
                Platform.startup(ready::countDown);
                toolkitStarted = ready.await(10, TimeUnit.SECONDS);
            } catch (IllegalStateException alreadyRunning) {
                toolkitStarted = true;
            } catch (Throwable noDisplay) {
                toolkitStarted = false;
            }
            if (toolkitStarted) {
                Platform.setImplicitExit(false);
            }
        }
        return toolkitStarted;
    }

    // ---------- Running on the JavaFX thread ----------

    /** Runs {@code action} on the JavaFX thread and waits, answering any dialog it opens. */
    protected void fx(Runnable action) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        long deadline = System.currentTimeMillis() + 30_000;
        try {
            while (!done.await(40, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Timed out waiting for the JavaFX thread");
                }
                Platform.runLater(this::answerDialogs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        // Dialogs opened with show() rather than showAndWait() are still open: answer them too.
        CountDownLatch swept = new CountDownLatch(1);
        Platform.runLater(() -> {
            answerDialogs();
            swept.countDown();
        });
        try {
            swept.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException r) {
            throw r;
        }
        if (t instanceof Error e) {
            throw e;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }

    private void answerDialogs() {
        for (Window window : new ArrayList<>(Window.getWindows())) {
            if (!window.isShowing() || window.getScene() == null
                    || !(window.getScene().getRoot() instanceof DialogPane pane)) {
                continue;
            }
            dialogsSeen.add(pane.getHeaderText() + " | " + pane.getContentText());
            ButtonBar.ButtonData wanted = onDialog.apply(pane);
            ButtonType chosen = null;
            for (ButtonType type : pane.getButtonTypes()) {
                if (type.getButtonData() == wanted) {
                    chosen = type;
                }
            }
            if (chosen == null) {
                for (ButtonType type : pane.getButtonTypes()) {
                    if (type.getButtonData().isCancelButton() || chosen == null) {
                        chosen = type;
                    }
                }
            }
            Node button = chosen == null ? null : pane.lookupButton(chosen);
            if (button instanceof ButtonBase base) {
                base.fire();
            }
            if (window.isShowing() && window instanceof Stage dialogStage) {
                dialogStage.close();
            }
        }
    }

    // ---------- Showing screens ----------

    /** Signs {@code username} in and opens their dashboard, as the login screen would. */
    protected void openDashboardAs(String username) {
        User user = data.users().find(username);
        fx(() -> {
            SessionManager.login(user);
            switch (user.getRole()) {
                case BUYER -> SceneManager.showBuyerDashboard();
                case SELLER -> SceneManager.showSellerDashboard();
                case ADMIN -> SceneManager.showAdminDashboard();
            }
            if (!stage.isShowing()) {
                stage.show();
            }
            layout();
        });
    }

    /** Opens a screen through the app's own navigation, e.g. {@code SceneManager::showBuyerLogin}. */
    protected void open(Runnable navigation) {
        fx(() -> {
            navigation.run();
            if (!stage.isShowing()) {
                stage.show();
            }
            layout();
        });
    }

    /** The current screen. */
    protected Parent root() {
        Scene scene = stage.getScene();
        return scene == null ? null : scene.getRoot();
    }

    private void layout() {
        Parent root = root();
        if (root != null) {
            root.applyCss();
            root.layout();
        }
    }

    // ---------- Finding and using controls ----------

    /** Every node of {@code type} on the current screen, including inside scroll panes and tabs. */
    protected <T extends Node> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (root() != null) {
            collect(root(), type, found, seen);
        }
        return found;
    }

    private static <T extends Node> void collect(Node node, Class<T> type, List<T> found, Set<Node> seen) {
        if (node == null || !seen.add(node)) {
            return;
        }
        if (type.isInstance(node)) {
            found.add(type.cast(node));
        }
        if (node instanceof ScrollPane scroll) {
            collect(scroll.getContent(), type, found, seen);
        }
        if (node instanceof TitledPane titled) {
            collect(titled.getContent(), type, found, seen);
        }
        if (node instanceof TabPane tabs) {
            for (Tab tab : tabs.getTabs()) {
                collect(tab.getContent(), type, found, seen);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, type, found, seen);
            }
        }
    }

    /** The first button or link whose text (ignoring surrounding spaces) is {@code text}. */
    protected ButtonBase button(String text) {
        return find(ButtonBase.class, b -> b.getText() != null && b.getText().trim().equals(text));
    }

    /** Whether a button or link with this text is on screen. */
    protected boolean hasButton(String text) {
        return all(ButtonBase.class).stream().anyMatch(b -> b.getText() != null && b.getText().trim().equals(text));
    }

    /** Clicks the first button or link with this text. */
    protected void click(String text) {
        fx(() -> {
            button(text).fire();
            layout();
        });
    }

    /** Clicks the button or link with this text if the screen shows one; returns whether it did. */
    protected boolean clickIfShown(String text) {
        boolean[] shown = new boolean[1];
        fx(() -> shown[0] = hasButton(text));
        if (shown[0]) {
            click(text);
        }
        return shown[0];
    }

    /** Clicks every button or link with this text that is on screen now, one after another. */
    protected int clickEach(String text) {
        List<ButtonBase> buttons = new ArrayList<>();
        fx(() -> buttons.addAll(all(ButtonBase.class).stream()
                .filter(b -> b.getText() != null && b.getText().trim().equals(text)).toList()));
        for (ButtonBase b : buttons) {
            fx(() -> {
                b.fire();
                layout();
            });
        }
        return buttons.size();
    }

    /** The first node of {@code type} matching {@code test}; fails if there is none. */
    protected <T extends Node> T find(Class<T> type, Predicate<T> test) {
        return all(type).stream().filter(test).findFirst()
                .orElseThrow(() -> new AssertionError("No matching " + type.getSimpleName() + " on screen"));
    }

    /** The text box whose prompt text starts with {@code prompt}. */
    protected TextInputControl field(String prompt) {
        return find(TextInputControl.class, f -> f.getPromptText() != null && f.getPromptText().startsWith(prompt));
    }

    /** Types into the text box with this prompt. */
    protected void type(String prompt, String text) {
        fx(() -> field(prompt).setText(text));
    }

    /** Picks item {@code index} in the {@code n}th combo box on screen. */
    protected void choose(int n, int index) {
        fx(() -> {
            ComboBox<?> box = all(ComboBox.class).get(n);
            box.getSelectionModel().select(index);
            if (box.getOnAction() != null) {
                box.getOnAction().handle(new javafx.event.ActionEvent());
            }
            layout();
        });
    }

    /** Picks item {@code index} in the combo box whose prompt text starts with {@code prompt}. */
    protected void choose(String prompt, int index) {
        fx(() -> {
            ComboBox<?> box = find(ComboBox.class,
                    c -> c.getPromptText() != null && c.getPromptText().startsWith(prompt));
            box.getSelectionModel().select(index);
            if (box.getOnAction() != null) {
                box.getOnAction().handle(new javafx.event.ActionEvent());
            }
            layout();
        });
    }

    /** Tries every option of every combo box on screen, then sets each back to its first option. */
    protected void tryEveryChoice() {
        int boxes = sizeOf(ComboBox.class);
        for (int n = 0; n < boxes; n++) {
            int box = n;
            int[] options = new int[1];
            fx(() -> options[0] = all(ComboBox.class).size() > box ? all(ComboBox.class).get(box).getItems().size() : 0);
            for (int i = Math.min(options[0], 6) - 1; i >= 0; i--) {
                choose(box, i);
            }
        }
    }

    private int sizeOf(Class<? extends Node> type) {
        int[] size = new int[1];
        fx(() -> size[0] = all(type).size());
        return size[0];
    }

    /** All visible text on the current screen, for assertions. */
    protected String screenText() {
        StringBuilder text = new StringBuilder();
        fx(() -> {
            for (Labeled labeled : all(Labeled.class)) {
                if (labeled.getText() != null) {
                    text.append(labeled.getText()).append('\n');
                }
            }
        });
        return text.toString();
    }
}
