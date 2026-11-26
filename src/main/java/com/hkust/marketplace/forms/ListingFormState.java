package com.hkust.marketplace.forms;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.LendingRules;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Location;
import com.hkust.marketplace.models.Money;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java state behind the create-listing form.
 *
 * <p>No JavaFX types live here: the file chooser and controls belong to the view, so this
 * class can be unit-tested without a toolkit. All common fields are plain values, which
 * means switching the listing type can never discard what the user already typed. The
 * three concrete listing kinds are disjoint subclasses (ADR-0004), so a value entered
 * for one type cannot leak into another type's saved object.
 */
public class ListingFormState {

    /** Maximum number of photos a listing may carry (mirrors {@code Listing}'s cap). */
    public static final int MAX_PHOTOS = 6;

    private ListingType type;
    private String title;
    private String description;
    private Category category;
    private Condition condition;
    private Location meetupLocation;
    private String rawPrice;
    private String rawDurationDays;
    private String rawDurationHours;
    private String rawDurationMinutes;
    private String rawDurationSeconds;

    private final List<PhotoDraft> chosenPhotos = new ArrayList<>();
    private List<String> photoPaths = new ArrayList<>();

    // --- Common fields -------------------------------------------------------------

    public ListingType getType() {
        return type;
    }

    public void setType(ListingType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Location getMeetupLocation() {
        return meetupLocation;
    }

    public void setMeetupLocation(Location meetupLocation) {
        this.meetupLocation = meetupLocation;
    }

    // --- Type-specific raw values --------------------------------------------------

    public String getRawPrice() {
        return rawPrice;
    }

    public void setRawPrice(String rawPrice) {
        this.rawPrice = rawPrice;
    }

    public String getRawDurationDays() {
        return rawDurationDays;
    }

    public void setRawDurationDays(String rawDurationDays) {
        this.rawDurationDays = rawDurationDays;
    }

    public String getRawDurationHours() {
        return rawDurationHours;
    }

    public void setRawDurationHours(String rawDurationHours) {
        this.rawDurationHours = rawDurationHours;
    }

    public String getRawDurationMinutes() {
        return rawDurationMinutes;
    }

    public void setRawDurationMinutes(String rawDurationMinutes) {
        this.rawDurationMinutes = rawDurationMinutes;
    }

    public String getRawDurationSeconds() {
        return rawDurationSeconds;
    }

    public void setRawDurationSeconds(String rawDurationSeconds) {
        this.rawDurationSeconds = rawDurationSeconds;
    }

    // --- Photos --------------------------------------------------------------------

    /** Source files chosen in the view, kept as pure-Java drafts. */
    public List<PhotoDraft> getChosenPhotos() {
        return Collections.unmodifiableList(chosenPhotos);
    }

    /**
     * Whether another photo can be added (at most six overall).
     *
     * <p>Photos already saved with the listing count toward the cap too, so an edit form can
     * never hold more than {@link #MAX_PHOTOS} photos between its stored and newly chosen ones.
     */
    public boolean canAddMorePhotos() {
        return photoPaths.size() + chosenPhotos.size() < MAX_PHOTOS;
    }

    /** Adds a photo chosen in the form, rejecting one past the overall cap. */
    public void addChosenPhoto(PhotoDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Photo draft must not be null");
        }
        if (!canAddMorePhotos()) {
            throw new IllegalStateException("A listing can have at most " + MAX_PHOTOS + " photos");
        }
        chosenPhotos.add(draft);
    }

    /** Removes a photo chosen in the form. */
    public void removeChosenPhoto(PhotoDraft draft) {
        chosenPhotos.remove(draft);
    }

    /** Project-root-relative paths written after storage; these are what the listing keeps. */
    public List<String> getPhotoPaths() {
        return Collections.unmodifiableList(photoPaths);
    }

    /** Sets the stored photo paths when editing an existing listing. */
    public void setPhotoPaths(List<String> photoPaths) {
        if (photoPaths != null && photoPaths.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("A listing can have at most " + MAX_PHOTOS + " photos");
        }
        this.photoPaths = photoPaths == null ? new ArrayList<>() : new ArrayList<>(photoPaths);
    }

    // --- Editing -------------------------------------------------------------------

    /**
     * Loads an existing listing into this state so the edit form can start prefilled.
     *
     * <p>The stored photo paths are copied for display, and the type-specific raw values are
     * taken from the concrete subclass. Values belonging to the other kinds are cleared first,
     * so a reused state can never leak a stale price or duration into the edited listing.
     *
     * @param listing the listing being edited
     */
    public void loadFrom(Listing listing) {
        if (listing == null) {
            throw new IllegalArgumentException("Listing must not be null");
        }
        setType(listing.getType());
        setTitle(listing.getTitle());
        setDescription(listing.getDescription());
        setCategory(listing.getCategory());
        setCondition(listing.getCondition());
        setMeetupLocation(listing.getMeetupLocation());
        setPhotoPaths(listing.getPhotoPaths());

        setRawPrice(null);
        setRawDurationDays(null);
        setRawDurationHours(null);
        setRawDurationMinutes(null);
        setRawDurationSeconds(null);

        if (listing instanceof SaleListing sale) {
            setRawPrice(sale.getPrice().amount().toPlainString());
        } else if (listing instanceof LendListing lend) {
            long totalSeconds = lend.getLendingDuration().getSeconds();
            setRawDurationDays(String.valueOf(totalSeconds / 86_400));
            setRawDurationHours(String.valueOf((totalSeconds % 86_400) / 3_600));
            setRawDurationMinutes(String.valueOf((totalSeconds % 3_600) / 60));
            setRawDurationSeconds(String.valueOf(totalSeconds % 60));
        }
    }

    // --- Validation ----------------------------------------------------------------

    /**
     * Validates the current state and returns field-key to message entries. Numeric rules
     * are delegated to {@link Money} and {@link LendingRules}; this class never reimplements
     * them.
     */
    public Map<String, String> validate(Seller seller) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (type == null) {
            errors.put("type", "Select a listing type.");
        }
        if (isBlank(title)) {
            errors.put("title", "Item name is required.");
        }
        if (isBlank(description)) {
            errors.put("description", "Description is required.");
        }
        if (category == null) {
            errors.put("category", "Category is required.");
        }
        if (condition == null) {
            errors.put("condition", "Condition is required.");
        }
        if (meetupLocation == null) {
            errors.put("location", "Meet-up location is required.");
        }
        if (seller == null) {
            errors.put("seller", "You must be logged in as a seller to post a listing.");
        }

        if (type == ListingType.SELL) {
            try {
                Money.of(rawPrice);
            } catch (IllegalArgumentException ex) {
                errors.put("price", ex.getMessage());
            }
        } else if (type == ListingType.LEND) {
            try {
                LendingRules.requireLendingDuration(parsedDuration());
            } catch (IllegalArgumentException ex) {
                errors.put("duration", ex.getMessage());
            }
        }

        return errors;
    }

    /** True when any field differs from its default, used for the dirty-cancel prompt. */
    public boolean isDirty() {
        return type != null
                || hasText(title) || hasText(description) || hasText(rawPrice)
                || hasText(rawDurationDays) || hasText(rawDurationHours)
                || hasText(rawDurationMinutes) || hasText(rawDurationSeconds)
                || category != null || condition != null || meetupLocation != null
                || !chosenPhotos.isEmpty() || !photoPaths.isEmpty();
    }

    // --- Builder -------------------------------------------------------------------

    /**
     * Builds only the active type's concrete subclass and attaches the stored photo paths.
     *
     * @throws IllegalStateException if the state still has validation errors
     */
    public Listing buildListing(Seller seller) {
        if (!validate(seller).isEmpty()) {
            throw new IllegalStateException("Cannot build a listing from an invalid form state");
        }

        Listing listing = switch (type) {
            case SELL -> new SaleListing(seller, title, description, category, condition,
                    meetupLocation, Money.of(rawPrice));
            case LEND -> new LendListing(seller, title, description, category, condition,
                    meetupLocation, LendingRules.requireLendingDuration(parsedDuration()));
            case GIVEAWAY -> new GiveawayListing(seller, title, description, category, condition,
                    meetupLocation);
        };

        for (String path : photoPaths) {
            listing.addPhoto(path);
        }
        return listing;
    }

    // --- Helpers -------------------------------------------------------------------

    public Duration getLendingDuration() {
        return LendingRules.requireLendingDuration(parsedDuration());
    }

    /** Parses the raw days/hours/minutes/seconds strings; numeric-rule checking stays in LendingRules. */
    private Duration parsedDuration() {
        String days = rawDurationDays == null ? "" : rawDurationDays.trim();
        String hours = rawDurationHours == null ? "" : rawDurationHours.trim();
        String minutes = rawDurationMinutes == null ? "" : rawDurationMinutes.trim();
        String seconds = rawDurationSeconds == null ? "" : rawDurationSeconds.trim();
        if (days.isEmpty() && hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty()) {
            throw new IllegalArgumentException("Lending duration is required");
        }
        try {
            long d = days.isEmpty() ? 0 : Long.parseLong(days);
            long h = hours.isEmpty() ? 0 : Long.parseLong(hours);
            long m = minutes.isEmpty() ? 0 : Long.parseLong(minutes);
            long s = seconds.isEmpty() ? 0 : Long.parseLong(seconds);
            if (d < 0 || h < 0 || m < 0 || s < 0) {
                throw new IllegalArgumentException("Lending duration must not be negative");
            }
            return Duration.ofDays(d).plusHours(h).plusMinutes(m).plusSeconds(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Lending days, hours, minutes and seconds must be whole numbers", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
