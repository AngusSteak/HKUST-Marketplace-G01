package com.hkust.marketplace.models;

/**
 * The password rules from UC-1.1 A3. A failed check returns the set of broken rules
 * so the UI can tell the user exactly what to fix.
 */
public enum PasswordRule {
    LENGTH("Password must be 8-64 characters."),
    UPPERCASE("Password must contain at least one uppercase letter."),
    LOWERCASE("Password must contain at least one lowercase letter."),
    DIGIT("Password must contain at least one digit."),
    SPECIAL("Password must contain at least one special character."),
    NO_SPACES("Password must not contain spaces."),
    NOT_USERNAME("Password must not be the same as the username.");

    private final String message;

    PasswordRule(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
