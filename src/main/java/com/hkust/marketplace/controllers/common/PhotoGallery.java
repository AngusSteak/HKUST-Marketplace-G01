package com.hkust.marketplace.controllers.common;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A small row of photo thumbnails for a listing, with a placeholder for missing files. */
public final class PhotoGallery {

    private static final double WIDTH = 150;
    private static final double HEIGHT = 110;

    private PhotoGallery() {
    }

    /** Wraps one thumbnail per path in a {@link FlowPane}. */
    public static FlowPane thumbnails(List<String> paths) {
        FlowPane gallery = new FlowPane(8, 8);
        for (String path : paths) {
            gallery.getChildren().add(thumb(path));
        }
        return gallery;
    }

    private static StackPane thumb(String path) {
        StackPane box = new StackPane();
        box.setPrefSize(WIDTH, HEIGHT);
        box.setMinSize(WIDTH, HEIGHT);
        box.setMaxSize(WIDTH, HEIGHT);
        box.setStyle("-fx-background-color: #eef2f7; -fx-background-radius: 8;");
        Label placeholder = new Label("No photo");
        placeholder.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        box.getChildren().add(placeholder);
        if (path == null) {
            return box;
        }
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            return box;
        }
        Image image = new Image(file.toUri().toString(), WIDTH, HEIGHT, true, true, true);
        if (image.isError()) {
            return box;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(WIDTH);
        view.setFitHeight(HEIGHT);
        view.setPreserveRatio(true);
        box.getChildren().setAll(view);
        return box;
    }
}
