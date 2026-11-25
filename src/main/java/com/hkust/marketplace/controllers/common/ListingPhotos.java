package com.hkust.marketplace.controllers.common;

import com.hkust.marketplace.models.Listing;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Listing photos, shared by the buyer and admin screens so photos look the same everywhere
 * (brief: consistent shared components across the three portals).
 */
public final class ListingPhotos {

    private ListingPhotos() {
    }

    /**
     * A photo, or a placeholder when there is none or the file is missing or unreadable. Photos
     * load in the background so the screen never waits on them; "Loading photo..." shows
     * underneath until the image appears, and turns into "No photo" if loading fails.
     */
    public static StackPane photo(String path, double width, double height) {
        StackPane box = new StackPane();
        box.setPrefSize(width, height);
        box.setMinSize(width, height);
        box.setMaxSize(width, height);
        box.setStyle("-fx-background-color: #eef2f7; -fx-background-radius: 8;");
        Label placeholder = new Label("No photo");
        placeholder.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        box.getChildren().add(placeholder);
        Path file = path == null ? null : Path.of(path);
        if (file == null || !Files.isRegularFile(file)) {
            return box;
        }
        Image image = new Image(file.toUri().toString(), width, height, true, true, true);
        if (image.isError()) {
            return box;
        }
        ImageView view = new ImageView(image);
        placeholder.setText("Loading photo...");
        box.getChildren().add(view);
        image.errorProperty().addListener((obs, was, failed) -> {
            if (failed) {
                box.getChildren().remove(view);
                placeholder.setText("No photo");
            }
        });
        return box;
    }

    /** All of a listing's photos (up to 6) in a wrapping row, or one placeholder if it has none. */
    public static FlowPane gallery(Listing listing, double width, double height) {
        FlowPane photos = new FlowPane(10, 10);
        List<String> paths = listing.getPhotoPaths();
        if (paths.isEmpty()) {
            photos.getChildren().add(photo(null, width, height));
        } else {
            paths.forEach(path -> photos.getChildren().add(photo(path, width, height)));
        }
        return photos;
    }
}
