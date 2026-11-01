package com.hkust.marketplace.utils;

import java.util.ArrayList;
import java.util.List;

public class PasswordValidator {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    public static final String REQUIRED = "Password is required.";
    public static final String TOO_SHORT = "Password must be at least " + MIN_LENGTH + " characters.";
    public static final String TOO_LONG = "Password must be at most " + MAX_LENGTH + " characters.";
    public static final String NO_UPPERCASE = "Password must contain an uppercase letter.";
    public static final String NO_LOWERCASE = "Password must contain a lowercase letter.";
    public static final String NO_DIGIT = "Password must contain a digit.";
    public static final String NO_SPECIAL = "Password must contain a special character.";
    public static final String HAS_SPACE = "Password must not contain spaces.";
    public static final String SAME_AS_USERNAME = "Password must not be the same as the username.";


    public static List<String> validate(String password, String username) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            errors.add(REQUIRED);
            return errors;
        }

        if (password.length() < MIN_LENGTH) errors.add(TOO_SHORT);
        if (password.length() > MAX_LENGTH) errors.add(TOO_LONG);
        if (password.chars().noneMatch(Character::isUpperCase)) errors.add(NO_UPPERCASE);
        if (password.chars().noneMatch(Character::isLowerCase)) errors.add(NO_LOWERCASE);
        if (password.chars().noneMatch(Character::isDigit)) errors.add(NO_DIGIT);
        if (password.chars().noneMatch(PasswordValidator::isSpecial)) errors.add(NO_SPECIAL);
        if (password.chars().anyMatch(Character::isWhitespace)) errors.add(HAS_SPACE);
        if (password.equals(username)) errors.add(SAME_AS_USERNAME);
        return errors;
    }

    private static boolean isSpecial(int c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
}
