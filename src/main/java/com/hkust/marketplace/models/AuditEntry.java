package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/** One immutable line of the admin audit log (SRS UC-3.3 S1): who, when, what, to what. */
public class AuditEntry {
    private final String admin;
    private final LocalDateTime time;
    private final String action;
    private final String target;

    /** Creates an audit entry. */
    public AuditEntry(String admin, LocalDateTime time, String action, String target) {
        this.admin = Objects.requireNonNull(admin, "admin");
        this.time = Objects.requireNonNull(time, "time");
        this.action = Objects.requireNonNull(action, "action");
        this.target = Objects.requireNonNull(target, "target");
    }

    public String getAdmin() { return admin; }
    public LocalDateTime getTime() { return time; }
    public String getAction() { return action; }
    public String getTarget() { return target; }

    @Override
    public String toString() {
        return time + " · " + admin + " · " + action + " · " + target;
    }
}
