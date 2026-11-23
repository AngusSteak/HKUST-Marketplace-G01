package com.hkust.marketplace.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Demo photo storage: copies chosen photos into a managed folder under the project root
 * and returns only a project-root-relative path for {@code Listing.photoPaths}.
 *
 * <p>A real backend can replace this implementation without changing callers; see
 * {@link PhotoStorage} for the mapping contract.
 */
public class LocalPhotoStorage implements PhotoStorage {

    /** Announced default from the design discussion. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Path ROOT = Path.of("photos");

    @Override
    public String store(Path source, String sellerUsername, String listingId) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Source photo must be an existing file");
        }

        String extension = extensionOf(source.getFileName().toString());
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("Only JPEG and PNG photos are supported");
        }
        try {
            if (Files.size(source) > MAX_BYTES) {
                throw new IllegalArgumentException("Photo must be at most 10 MB");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the photo file", ex);
        }

        String safeUser = safeSegment(sellerUsername, "seller username");
        String safeId = safeSegment(listingId, "listing id");
        String filename = UUID.randomUUID() + "." + extension;
        Path directory = ROOT.resolve(safeUser).resolve(safeId);
        Path destination = directory.resolve(filename);

        try {
            Files.createDirectories(directory);
            if (Files.exists(destination)) {
                throw new IllegalStateException("Destination already exists: " + destination);
            }
            Files.copy(source, destination);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store the photo", ex);
        }

        return "photos/" + safeUser + "/" + safeId + "/" + filename;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isAllowedExtension(String extension) {
        return extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png");
    }

    /** Reduces a value to one safe path segment so no traversal or separator can slip in. */
    private static String safeSegment(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String safe = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return safe;
    }
}
