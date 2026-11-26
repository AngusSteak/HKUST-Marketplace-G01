package com.hkust.marketplace.forms;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A standalone, reusable create/edit listing form.
 *
 * <p>The component owns a {@link ListingFormState} and exposes only neutral hooks
 * ({@link #getView()}, {@link #validate()}, {@link #setOnSubmit(Consumer)}), so a future
 * admin-on-behalf flow can call it without seller-portal assumptions. The type-specific
 * controls are created once and merely hidden when irrelevant, so typed values survive a
 * type switch by construction.
 */
public class ListingFormComponent {

    private final Supplier<Seller> sellerSupplier;
    private final ListingFormState state = new ListingFormState();
    private final VBox root = new VBox(12);
    private final Label heading = new Label("Post a new item");
    private final Map<String, Label> errorLabels = new LinkedHashMap<>();
    private final Map<String, Node> fieldControls = new HashMap<>();

    private final ComboBox<String> typeCombo =
            new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
    private final TextField nameField = new TextField();
    private final ComboBox<Category> categoryCombo =
            new ComboBox<>(FXCollections.observableArrayList(Category.values()));
    private final ComboBox<Condition> conditionCombo =
            new ComboBox<>(FXCollections.observableArrayList(Condition.values()));
    private final ComboBox<Location> locationCombo =
            new ComboBox<>(FXCollections.observableArrayList(Location.values()));
    private final TextArea descriptionArea = new TextArea();

    private final TextField priceField = new TextField();
    private final TextField daysField = new TextField();
    private final TextField hoursField = new TextField();
    private final TextField minutesField = new TextField();
    private final TextField secondsField = new TextField();

    private final HBox priceRow = new HBox(8);
    private final HBox durationRow = new HBox(8);
    private final VBox durationSection = new VBox(6);
    private final VBox photoArea = new VBox(8);
    private final HBox thumbnailStrip = new HBox(8);
    private final Button addPhotosButton = new Button("Add photos");
    private final Label photoHint = new Label();

    private final Button submitButton = new Button("Submit for Review");
    private final Button cancelButton = new Button("Cancel");

    private Consumer<ListingFormState> onSubmit;
    private Runnable onCancel;

    /** Creates the form; the seller is read when the form is submitted. */
    public ListingFormComponent(Supplier<Seller> sellerSupplier, Consumer<ListingFormState> onSubmit) {
        this.sellerSupplier = sellerSupplier;
        this.onSubmit = onSubmit;
        buildView();
        bindControls();
        updateTypeFields();
    }

    /** The root node, placeable in any Pane. */
    public Node getView() {
        return root;
    }

    /** The state this component is editing (used by the submit pipeline). */
    public ListingFormState getState() {
        return state;
    }

    /** Validates against the supplied seller. */
    public Map<String, String> validate() {
        return state.validate(sellerSupplier.get());
    }

    /** Sets what runs when the form is submitted. */
    public void setOnSubmit(Consumer<ListingFormState> onSubmit) {
        this.onSubmit = onSubmit;
    }

    /** Lets the admin-on-behalf flow label the button for its own outcome (saved as Live). */
    public void setSubmitLabel(String label) {
        submitButton.setText(label);
    }

    /**
     * Fills every control from an existing listing so it can be edited. The listing type is
     * locked, and photos already saved with the listing are shown as read-only thumbnails.
     *
     * @param listing the listing being edited
     */
    public void prefillFrom(Listing listing) {
        state.loadFrom(listing);

        typeCombo.setValue(typeLabel(listing.getType()));
        nameField.setText(state.getTitle());
        descriptionArea.setText(state.getDescription());
        categoryCombo.setValue(state.getCategory());
        conditionCombo.setValue(state.getCondition());
        locationCombo.setValue(state.getMeetupLocation());
        if (listing instanceof SaleListing) {
            priceField.setText(state.getRawPrice());
        } else if (listing instanceof LendListing) {
            daysField.setText(state.getRawDurationDays());
            hoursField.setText(state.getRawDurationHours());
            minutesField.setText(state.getRawDurationMinutes());
            secondsField.setText(state.getRawDurationSeconds());
        }

        updateTypeFields();
        typeCombo.setDisable(true);
        heading.setText("Edit item");
        setSubmitLabel("Save and resubmit");
        rebuildThumbnails();
    }

    /** Renders a field-key to message map into the per-field error labels. */
    public void showErrors(Map<String, String> errors) {
        clearErrors();
        errors.forEach((key, message) -> {
            Label label = errorLabels.get(key);
            if (label != null) {
                label.setText(message);
                label.setVisible(true);
                label.setManaged(true);
            }
            Node control = fieldControls.get(key);
            if (control != null) {
                control.getStyleClass().add("input-error");
            }
        });
    }

    /** Clears every error label without touching any input value. */
    public void clearErrors() {
        errorLabels.values().forEach(label -> {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        });
        fieldControls.values().forEach(control -> control.getStyleClass().remove("input-error"));
    }

    /** Clears only one field's error, leaving its typed value intact. */
    private void clearError(String key) {
        Label label = errorLabels.get(key);
        if (label != null) {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        }
        Node control = fieldControls.get(key);
        if (control != null) {
            control.getStyleClass().remove("input-error");
        }
    }

    /** Sets what runs when the form is cancelled. */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /** The photo area, filled by the photo chooser step. */
    protected VBox getPhotoArea() {
        return photoArea;
    }

    protected Button getCancelButton() {
        return cancelButton;
    }

    protected Button getSubmitButton() {
        return submitButton;
    }

    // --- Construction ---------------------------------------------------------------

    private void buildView() {
        root.getStyleClass().add("card");
        root.setPadding(new Insets(20));
        root.setMaxWidth(560);

        heading.getStyleClass().add("section-title");

        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        nameField.setPromptText("Item name *");
        categoryCombo.setPromptText("Category *");
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        conditionCombo.setPromptText("Condition *");
        conditionCombo.setMaxWidth(Double.MAX_VALUE);
        locationCombo.setPromptText("Meet-up location *");
        locationCombo.setMaxWidth(Double.MAX_VALUE);
        descriptionArea.setPromptText("Description");
        descriptionArea.setPrefRowCount(3);

        priceField.setPromptText("Price (HK$)");
        priceField.setMaxWidth(Double.MAX_VALUE);
        priceRow.getChildren().add(priceField);
        HBox.setHgrow(priceField, Priority.ALWAYS);

        daysField.setPromptText("0");
        hoursField.setPromptText("0");
        minutesField.setPromptText("0");
        secondsField.setPromptText("0");
        for (TextField field : new TextField[]{daysField, hoursField, minutesField, secondsField}) {
            field.setMaxWidth(Double.MAX_VALUE);
        }
        durationRow.getChildren().addAll(
                durationField("Days", daysField),
                durationField("Hours", hoursField),
                durationField("Minutes", minutesField),
                durationField("Seconds", secondsField));
        Label durationHint = new Label(
                "Enter days / hours / minutes / seconds (minimum total 5 seconds).");
        durationHint.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        durationSection.getChildren().addAll(durationRow, durationHint);

        setupPhotoChooser();

        submitButton.getStyleClass().add("btn-gold");
        submitButton.setOnAction(event -> {
            if (onSubmit != null) {
                onSubmit.accept(state);
            }
        });
        cancelButton.getStyleClass().add("btn-outline");
        cancelButton.setOnAction(event -> {
            if (state.isDirty()) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Discard your unsaved changes and return to My Listings?");
                confirmation.setHeaderText("Unsaved changes");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }
            if (onCancel != null) {
                onCancel.run();
            }
        });

        HBox actions = new HBox(10, submitButton, cancelButton);

        root.getChildren().addAll(
                heading,
                field("Listing Type *", typeCombo, "type"),
                field("Item Name *", nameField, "title"),
                field("Category *", categoryCombo, "category"),
                field("Condition *", conditionCombo, "condition"),
                field("Meet-up Location *", locationCombo, "location"),
                field("Description *", descriptionArea, "description"),
                field("Price (Sell only)", priceRow, "price"),
                field("Lending Duration (Lend only: days, hours, minutes, seconds)", durationSection, "duration"),
                photoArea,
                actions);
    }

    private static VBox durationField(String unit, TextField field) {
        Label unitLabel = new Label(unit);
        unitLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        VBox box = new VBox(4, unitLabel, field);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Wraps a control with a caption and an (initially hidden) error label. */
    private VBox field(String caption, Node control, String errorKey) {
        Label captionLabel = new Label(caption);
        VBox box = new VBox(4, captionLabel, control);
        if (errorKey != null) {
            Label errorLabel = new Label();
            errorLabel.getStyleClass().add("input-error-label");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            errorLabels.put(errorKey, errorLabel);
            fieldControls.put(errorKey, control);
            box.getChildren().add(errorLabel);
        }
        return box;
    }

    private void setupPhotoChooser() {
        addPhotosButton.getStyleClass().add("btn-outline");
        addPhotosButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose photos (JPEG/PNG, up to 6)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images", "*.jpg", "*.jpeg", "*.png"));
            var window = root.getScene() == null ? null : root.getScene().getWindow();
            List<File> files = chooser.showOpenMultipleDialog(window);
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (!state.canAddMorePhotos()) {
                    break;
                }
                if (isSupportedImage(file.getName())) {
                    state.addChosenPhoto(new PhotoDraft(file.toPath(), file.getName()));
                }
            }
            rebuildThumbnails();
        });

        photoArea.getChildren().addAll(
                new Label("Photos (up to 6, JPEG/PNG)"),
                addPhotosButton,
                photoHint,
                thumbnailStrip);
        rebuildThumbnails();
    }

    private static boolean isSupportedImage(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png");
    }

    private void rebuildThumbnails() {
        thumbnailStrip.getChildren().clear();
        for (String path : state.getPhotoPaths()) {
            thumbnailStrip.getChildren().add(storedPhotoThumbnail(path));
        }
        for (PhotoDraft draft : state.getChosenPhotos()) {
            VBox thumbnail = new VBox(4);
            try {
                Image image = new Image(draft.source().toUri().toString(), 80, 80, true, true);
                ImageView view = new ImageView(image);
                view.setFitWidth(80);
                view.setFitHeight(80);
                view.setPreserveRatio(true);
                thumbnail.getChildren().add(view);
            } catch (RuntimeException ex) {
                thumbnail.getChildren().add(new Label("Preview unavailable"));
            }
            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("btn-danger", "btn-sm");
            PhotoDraft target = draft;
            remove.setOnAction(event -> {
                state.removeChosenPhoto(target);
                rebuildThumbnails();
            });
            thumbnail.getChildren().add(remove);
            thumbnailStrip.getChildren().add(thumbnail);
        }
        addPhotosButton.setDisable(!state.canAddMorePhotos());
        photoHint.setText(photoHintText());
    }

    private String photoHintText() {
        if (state.canAddMorePhotos()) {
            return "";
        }
        if (state.getChosenPhotos().isEmpty()) {
            return "Maximum of " + ListingFormState.MAX_PHOTOS + " photos reached. "
                    + "Saved photos cannot be removed here.";
        }
        return "Maximum of " + ListingFormState.MAX_PHOTOS + " photos reached. Remove one to add another.";
    }

    private VBox storedPhotoThumbnail(String path) {
        VBox thumbnail = new VBox(4);
        thumbnail.getChildren().add(storedPhotoImage(path));
        Label saved = new Label("Saved photo");
        saved.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px;");
        thumbnail.getChildren().add(saved);
        return thumbnail;
    }

    private Node storedPhotoImage(String path) {
        if (path != null && !path.isBlank()) {
            try {
                Path file = Path.of(path).toAbsolutePath();
                if (Files.isRegularFile(file)) {
                    Image image = new Image(file.toUri().toString(), 80, 80, true, true);
                    if (!image.isError()) {
                        ImageView view = new ImageView(image);
                        view.setFitWidth(80);
                        view.setFitHeight(80);
                        view.setPreserveRatio(true);
                        return view;
                    }
                }
            } catch (RuntimeException ex) {
                // The placeholder below covers an unreadable or missing file.
            }
        }
        Label missing = new Label("Photo unavailable");
        missing.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        missing.setWrapText(true);
        missing.setPrefWidth(80);
        return missing;
    }

    private void bindControls() {
        typeCombo.valueProperty().addListener((obs, old, value) -> {
            state.setType(toListingType(value));
            updateTypeFields();
            clearError("type");
        });
        nameField.textProperty().addListener((obs, old, value) -> {
            state.setTitle(value);
            clearError("title");
        });
        descriptionArea.textProperty().addListener((obs, old, value) -> {
            state.setDescription(value);
            clearError("description");
        });
        categoryCombo.valueProperty().addListener((obs, old, value) -> {
            state.setCategory(value);
            clearError("category");
        });
        conditionCombo.valueProperty().addListener((obs, old, value) -> {
            state.setCondition(value);
            clearError("condition");
        });
        locationCombo.valueProperty().addListener((obs, old, value) -> {
            state.setMeetupLocation(value);
            clearError("location");
        });
        priceField.textProperty().addListener((obs, old, value) -> {
            state.setRawPrice(value);
            clearError("price");
        });
        daysField.textProperty().addListener((obs, old, value) -> {
            state.setRawDurationDays(value);
            clearError("duration");
        });
        hoursField.textProperty().addListener((obs, old, value) -> {
            state.setRawDurationHours(value);
            clearError("duration");
        });
        minutesField.textProperty().addListener((obs, old, value) -> {
            state.setRawDurationMinutes(value);
            clearError("duration");
        });
        secondsField.textProperty().addListener((obs, old, value) -> {
            state.setRawDurationSeconds(value);
            clearError("duration");
        });
    }

    /** Shows exactly the active type's specific row; never re-creates controls. */
    private void updateTypeFields() {
        String value = typeCombo.getValue();
        boolean sell = "Sell".equals(value);
        boolean lend = "Lend".equals(value);
        priceRow.setVisible(sell);
        priceRow.setManaged(sell);
        durationSection.setVisible(lend);
        durationSection.setManaged(lend);
    }

    private static ListingType toListingType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "Sell" -> ListingType.SELL;
            case "Lend" -> ListingType.LEND;
            case "Give Away" -> ListingType.GIVEAWAY;
            default -> null;
        };
    }

    private static String typeLabel(ListingType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SELL -> "Sell";
            case LEND -> "Lend";
            case GIVEAWAY -> "Give Away";
        };
    }
}
