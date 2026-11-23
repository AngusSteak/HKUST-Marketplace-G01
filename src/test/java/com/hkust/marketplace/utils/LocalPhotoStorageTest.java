package com.hkust.marketplace.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPhotoStorageTest {

    @TempDir
    Path tempDir;

    private final LocalPhotoStorage storage = new LocalPhotoStorage();
    private final List<Path> createdDirectories = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (Path directory : createdDirectories) {
            if (Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                    // best-effort cleanup of demo files
                                }
                            });
                }
            }
        }
    }

    private Path sourceFile(String name, byte[] bytes) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    @Test
    void storeCopiesBytesAndReturnsARelativePath() throws IOException {
        byte[] bytes = {1, 2, 3, 4, 5};
        Path source = sourceFile("photo.PNG", bytes);
        createdDirectories.add(Path.of("photos", "charlie", "LTEST1"));

        String stored = storage.store(source, "charlie", "LTEST1");

        assertTrue(stored.matches("photos/charlie/LTEST1/[0-9a-f-]+\\.png"),
                "unexpected stored path: " + stored);
        assertFalse(Path.of(stored).isAbsolute());
        Path destination = Path.of(stored);
        assertTrue(Files.exists(destination));
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, Files.readAllBytes(destination));
    }

    @Test
    void aWrongExtensionIsRejectedAndCreatesNoFile() throws IOException {
        Path source = sourceFile("photo.gif", new byte[]{1, 2, 3});
        createdDirectories.add(Path.of("photos", "charlie", "LTEST2"));

        assertThrows(IllegalArgumentException.class,
                () -> storage.store(source, "charlie", "LTEST2"));
        assertFalse(Files.exists(Path.of("photos", "charlie", "LTEST2")));
    }

    @Test
    void anOversizedFileIsRejectedAndCreatesNoFile() throws IOException {
        Path source = sourceFile("big.png", new byte[11 * 1024 * 1024]);
        createdDirectories.add(Path.of("photos", "charlie", "LTEST3"));

        assertThrows(IllegalArgumentException.class,
                () -> storage.store(source, "charlie", "LTEST3"));
        assertFalse(Files.exists(Path.of("photos", "charlie", "LTEST3")));
    }

    @Test
    void unsafeCharactersAreSanitisedIntoOneSegment() throws IOException {
        Path source = sourceFile("photo.jpg", new byte[]{9, 8, 7});
        createdDirectories.add(Path.of("photos", "___evil", "LTEST4"));

        String stored = storage.store(source, "../evil", "LTEST4");

        assertTrue(stored.startsWith("photos/___evil/LTEST4/"), "unexpected path: " + stored);
        assertFalse(stored.contains("/../"));
        assertTrue(Files.exists(Path.of(stored)));
    }

    @Test
    void aBlankSellerNameIsRejected() throws IOException {
        Path source = sourceFile("photo.png", new byte[]{1});
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(source, "   ", "LTEST5"));
    }

    @Test
    void punctuationOnlyNamesBecomeOneSafeSegment() throws IOException {
        Path source = sourceFile("photo.png", new byte[]{1});
        createdDirectories.add(Path.of("photos", "___", "LTEST6"));

        String stored = storage.store(source, "///", "LTEST6");

        assertTrue(stored.startsWith("photos/___/LTEST6/"), "unexpected path: " + stored);
        assertTrue(Files.exists(Path.of(stored)));
    }
}
