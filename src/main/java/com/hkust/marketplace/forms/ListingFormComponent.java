package com.hkust.marketplace.forms;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Seller;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A standalone, reusable create-listing form.
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
    private final Map<String, Label> errorLabels = new LinkedHashMap<>();

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
    private final TextField secondsField = new TextField();

    private final HBox priceRow = new HBox(8);
    private final HBox durationRow = new HBox(8);
    private final VBox photoArea = new VBox(8);
    private final HBox thumbnailStrip = new HBox(8);
    private final Button addPhotosButton = new Button("Add photos");
    private final Label photoHint = new Label();

    private final Button submitButton = new Button("Submit for Review");
    private final Button cancelButton = new Button("Cancel");

    private Consumer<ListingFormState> onSubmit;

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

    public void setOnSubmit(Consumer<ListingFormState> onSubmit) {
        this.onSubmit = onSubmit;
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
        });
    }

    /** Clears every error label without touching any input value. */
    public void clearErrors() {
        errorLabels.values().forEach(label -> {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        });
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

        Label heading = new Label("Post a new item");
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

        daysField.setPromptText("Days");
        hoursField.setPromptText("Hours");
        secondsField.setPromptText("Seconds (min 5)");
        for (TextField field : new TextField[]{daysField, hoursField, secondsField}) {
            field.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(field, Priority.ALWAYS);
        }
        durationRow.getChildren().addAll(daysField, hoursField, secondsField);

        setupPhotoChooser();

        submitButton.getStyleClass().add("btn-gold");
        submitButton.setOnAction(event -> {
            if (onSubmit != null) {
                onSubmit.accept(state);
            }
        });
        cancelButton.getStyleClass().add("btn-outline");

        HBox actions = new HBox(10, submitButton, cancelButton);

        root.getChildren().addAll(
                heading,
                field("Listing Type *", typeCombo, "type"),
                field("Item Name *", nameField, "title"),
                field("Category *", categoryCombo, "category"),
                field("Condition *", conditionCombo, "condition"),
                field("Meet-up Location *", locationCombo, "location"),
                field("Description", descriptionArea, null),
                field("Price (Sell only)", priceRow, "price"),
                field("Lending Duration (Lend only)", durationRow, "duration"),
                photoArea,
                actions);
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
        photoHint.setText(state.canAddMorePhotos()
                ? ""
                : "Maximum of 6 photos reached. Remove one to add another.");
    }

    private void bindControls() {
        typeCombo.valueProperty().addListener((obs, old, value) -> {
            state.setType(toListingType(value));
            updateTypeFields();
        });
        nameField.textProperty().addListener((obs, old, value) -> state.setTitle(value));
        descriptionArea.textProperty().addListener((obs, old, value) -> state.setDescription(value));
        categoryCombo.valueProperty().addListener((obs, old, value) -> state.setCategory(value));
        conditionCombo.valueProperty().addListener((obs, old, value) -> state.setCondition(value));
        locationCombo.valueProperty().addListener((obs, old, value) -> state.setMeetupLocation(value));
        priceField.textProperty().addListener((obs, old, value) -> state.setRawPrice(value));
        daysField.textProperty().addListener((obs, old, value) -> state.setRawDurationDays(value));
        hoursField.textProperty().addListener((obs, old, value) -> state.setRawDurationHours(value));
        secondsField.textProperty().addListener((obs, old, value) -> state.setRawDurationSeconds(value));
    }

    /** Shows exactly the active type's specific row; never re-creates controls. */
    private void updateTypeFields() {
        String value = typeCombo.getValue();
        boolean sell = "Sell".equals(value);
        boolean lend = "Lend".equals(value);
        priceRow.setVisible(sell);
        priceRow.setManaged(sell);
        durationRow.setVisible(lend);
        durationRow.setManaged(lend);
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
}
