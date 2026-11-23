package com.hkust.marketplace.forms;

import java.nio.file.Path;

/**
 * A photo the user has chosen in the form but that has not been stored yet.
 *
 * <p>Pure Java on purpose: the form state that holds these drafts must be unit-testable
 * without a JavaFX toolkit. The {@code FileChooser} and thumbnail rendering live in the
 * view; this carrier only records the source file and a short display name.
 *
 * @param source      the source file chosen by the user
 * @param displayName a short name shown next to the thumbnail
 */
public record PhotoDraft(Path source, String displayName) {
}
