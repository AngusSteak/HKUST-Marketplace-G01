package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Where an OPEN notification should take the user. Stores the kind of object and its id
 * rather than a live object, so notifications survive listings and deals being replaced.
 */
public final class NotificationRef {

    public enum Target { LISTING, REQUEST, DEAL, THREAD, REVIEW, USER }

    private final Target target;
    private final String id;

    public NotificationRef(Target target, String id) {
        this.target = Objects.requireNonNull(target, "target");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Related id must not be blank");
        }
        this.id = id.trim();
    }

    public Target getTarget() { return target; }
    public String getId() { return id; }

    @Override
    public String toString() {
        return target + ":" + id;
    }
}
