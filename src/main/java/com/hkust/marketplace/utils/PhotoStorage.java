package com.hkust.marketplace.utils;

import java.nio.file.Path;

/**
 * Stores a chosen photo and returns the project-root-relative path that should be
 * written into {@code Listing.photoPaths}, of the form
 * {@code photos/<sellerUsername>/<listingId>/<uuid>.<ext>}.
 *
 * <p>The returned string is deliberately relative so the same data maps cleanly onto a
 * future database: a DB-backed implementation can be swapped in and map these strings
 * to a {@code listing_photos(listing_id, path, ordinal)} row without changing callers.
 * Implementations must never return absolute paths.
 */
public interface PhotoStorage {

    /**
     * Copies one chosen photo into storage.
     *
     * @param source        the source file chosen by the user
     * @param sellerUsername the posting seller's username (used as a path segment)
     * @param listingId     the immutable id reserved for the listing before save
     * @return a project-root-relative path string to store in {@code Listing.photoPaths}
     */
    String store(Path source, String sellerUsername, String listingId);
}
