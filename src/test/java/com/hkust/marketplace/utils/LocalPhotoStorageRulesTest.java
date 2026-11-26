package com.hkust.marketplace.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Photos that are refused before anything is copied (SRS UC-2.3): a missing file, a type other
 * than JPEG/PNG, or no seller or listing to file it under. None of these write to data/photos.
 */
class LocalPhotoStorageRulesTest {

    @TempDir
    Path temp;

    private final LocalPhotoStorage storage = new LocalPhotoStorage();

    @Test
    void theSourceMustBeAnExistingFile() {
        assertThrows(IllegalArgumentException.class, () -> storage.store(null, "charlie", "L1"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(temp.resolve("missing.jpg"), "charlie", "L1"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(temp, "charlie", "L1"));
    }

    @Test
    void onlyJpegAndPngAreAccepted() throws IOException {
        Path noExtension = Files.writeString(temp.resolve("photo"), "x");
        Path gif = Files.writeString(temp.resolve("photo.gif"), "x");

        assertThrows(IllegalArgumentException.class, () -> storage.store(noExtension, "charlie", "L1"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(gif, "charlie", "L1"));
    }

    @Test
    void aSellerAndListingAreRequired() throws IOException {
        Path jpeg = Files.writeString(temp.resolve("photo.JPEG"), "x");

        assertThrows(IllegalArgumentException.class, () -> storage.store(jpeg, null, "L1"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(jpeg, " ", "L1"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(jpeg, "charlie", null));
    }
}
