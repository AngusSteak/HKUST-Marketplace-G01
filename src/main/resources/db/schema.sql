-- HKUST Marketplace SQLite schema (SRS class diagram, Appendix A).
-- Applied on every start; CREATE ... IF NOT EXISTS keeps existing data.
-- Encoding: timestamps ISO-8601 TEXT, money decimal TEXT, durations whole seconds,
-- booleans INTEGER 0/1, enums by constant name (see persistence/Database.java).
-- Usernames are stored lower-case, so they are unique across all roles (ADR-0003).

-- ---------- Users (persisted by UserStore, #55) ----------

CREATE TABLE IF NOT EXISTS users (
    username        TEXT PRIMARY KEY,
    full_name       TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),
    active          INTEGER NOT NULL DEFAULT 1,
    deleted         INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL,
    residence_hall  TEXT,            -- buyers only
    bio             TEXT             -- sellers only
);

CREATE TABLE IF NOT EXISTS seller_meetup_locations (
    username  TEXT NOT NULL REFERENCES users (username) ON DELETE CASCADE,
    location  TEXT NOT NULL,
    PRIMARY KEY (username, location)
);

-- ---------- Listings (persisted by ListingStore, #55) ----------

CREATE TABLE IF NOT EXISTS listings (
    id                        TEXT PRIMARY KEY,
    type                      TEXT NOT NULL CHECK (type IN ('SELL', 'LEND', 'GIVEAWAY')),
    seller_username           TEXT NOT NULL REFERENCES users (username),
    title                     TEXT NOT NULL,
    description               TEXT NOT NULL,
    category                  TEXT NOT NULL,
    condition                 TEXT NOT NULL,
    meetup_location           TEXT NOT NULL,
    status                    TEXT NOT NULL,
    submitted_date            TEXT NOT NULL,
    reviewed_at               TEXT,
    rejection_reason          TEXT,
    takedown_reason           TEXT,
    view_count                INTEGER NOT NULL DEFAULT 0,
    deleted                   INTEGER NOT NULL DEFAULT 0,  -- soft-delete visibility flag
    price                     TEXT,      -- SELL only
    lending_duration_seconds  INTEGER,   -- LEND only
    CHECK ((type = 'SELL') = (price IS NOT NULL)),
    CHECK ((type = 'LEND') = (lending_duration_seconds IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS listing_photos (
    listing_id  TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL CHECK (position BETWEEN 0 AND 5),
    path        TEXT NOT NULL,
    PRIMARY KEY (listing_id, position)
);

-- ---------- Deals (persisted by DealStore, #56) ----------

CREATE TABLE IF NOT EXISTS deal_requests (
    id              TEXT PRIMARY KEY,
    type            TEXT NOT NULL CHECK (type IN ('OFFER', 'BORROW', 'CLAIM')),
    buyer_username  TEXT NOT NULL REFERENCES users (username),
    listing_id      TEXT NOT NULL REFERENCES listings (id),
    submitted_time  TEXT NOT NULL,
    status          TEXT NOT NULL,
    offered_price   TEXT,   -- OFFER
    counter_price   TEXT,   -- OFFER, when countered
    start_date      TEXT,   -- BORROW
    end_date        TEXT,   -- BORROW
    counter_start   TEXT,   -- BORROW, when countered
    counter_end     TEXT    -- BORROW, when countered
);

CREATE TABLE IF NOT EXISTS deals (
    id              TEXT PRIMARY KEY,
    request_id      TEXT NOT NULL UNIQUE REFERENCES deal_requests (id),
    start_date      TEXT NOT NULL,
    due_date        TEXT,   -- loans only
    completed_date  TEXT,
    status          TEXT NOT NULL
);

-- ---------- Favourites and follows (persisted by FavouriteStore, #56) ----------

CREATE TABLE IF NOT EXISTS favourites (
    buyer_username  TEXT NOT NULL REFERENCES users (username),
    listing_id      TEXT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,   -- keeps the order they were added
    PRIMARY KEY (buyer_username, listing_id)
);

CREATE TABLE IF NOT EXISTS follows (
    buyer_username   TEXT NOT NULL REFERENCES users (username),
    seller_username  TEXT NOT NULL REFERENCES users (username),
    position         INTEGER NOT NULL,
    PRIMARY KEY (buyer_username, seller_username)
);

-- ---------- Chat (persisted by ChatStore, #57) ----------

CREATE TABLE IF NOT EXISTS chat_threads (
    buyer_username  TEXT NOT NULL REFERENCES users (username),
    listing_id      TEXT NOT NULL REFERENCES listings (id),
    PRIMARY KEY (buyer_username, listing_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,   -- also the send order
    buyer_username   TEXT NOT NULL,
    listing_id       TEXT NOT NULL,
    sender_username  TEXT NOT NULL REFERENCES users (username),
    content          TEXT NOT NULL,
    sent_time        TEXT NOT NULL,
    deal_request_id  TEXT REFERENCES deal_requests (id),  -- set for a Formal Offer
    FOREIGN KEY (buyer_username, listing_id) REFERENCES chat_threads (buyer_username, listing_id)
);

-- ---------- Notifications, reviews, reports, audit (persisted by #58) ----------

CREATE TABLE IF NOT EXISTS notifications (
    id                  TEXT PRIMARY KEY,
    recipient_username  TEXT NOT NULL REFERENCES users (username),
    title               TEXT NOT NULL,
    message             TEXT NOT NULL,
    category            TEXT NOT NULL,
    related_target      TEXT,   -- NotificationRef.Target, null for announcements
    related_id          TEXT,
    timestamp           TEXT NOT NULL,
    read                INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS reviews (
    id               TEXT PRIMARY KEY,
    deal_id          TEXT REFERENCES deals (id),   -- one review per completed deal
    seller_username  TEXT NOT NULL REFERENCES users (username),
    buyer_username   TEXT NOT NULL REFERENCES users (username),
    listing_title    TEXT,
    rating           INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment          TEXT,
    seller_reply     TEXT,
    hidden           INTEGER NOT NULL DEFAULT 0,
    flagged          INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT NOT NULL,
    UNIQUE (deal_id)
);

CREATE TABLE IF NOT EXISTS listing_reports (
    id              TEXT PRIMARY KEY,
    buyer_username  TEXT NOT NULL REFERENCES users (username),
    listing_id      TEXT NOT NULL REFERENCES listings (id),
    reason          TEXT NOT NULL,
    reported_time   TEXT NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution      TEXT
);

CREATE TABLE IF NOT EXISTS audit_log (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,   -- append-only, also the order
    admin   TEXT NOT NULL,
    time    TEXT NOT NULL,
    action  TEXT NOT NULL,
    target  TEXT NOT NULL
);
