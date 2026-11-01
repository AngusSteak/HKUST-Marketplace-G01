package com.hkust.marketplace.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    private static final String USERNAME = "alice";

    @Test
    void validPasswordHasNoErrors() {
        assertTrue(PasswordValidator.validate("Abcdef1!", USERNAME).isEmpty());
    }

    @Test
    void nullPasswordIsRequired() {
        assertEquals(List.of(PasswordValidator.REQUIRED), PasswordValidator.validate(null, USERNAME));
    }

    @Test
    void emptyPasswordIsRequired() {
        assertEquals(List.of(PasswordValidator.REQUIRED), PasswordValidator.validate("", USERNAME));
    }

    @Test
    void sevenCharactersIsTooShort() {
        assertEquals(List.of(PasswordValidator.TOO_SHORT), PasswordValidator.validate("Abcde1!", USERNAME));
    }

    @Test
    void eightCharactersIsAccepted() {
        String password = "Abcdef1!";
        assertEquals(8, password.length());
        assertTrue(PasswordValidator.validate(password, USERNAME).isEmpty());
    }

    @Test
    void sixtyFourCharactersIsAccepted() {
        String password = "Aa1!" + "x".repeat(60);
        assertEquals(64, password.length());
        assertTrue(PasswordValidator.validate(password, USERNAME).isEmpty());
    }

    @Test
    void sixtyFiveCharactersIsTooLong() {
        String password = "Aa1!" + "x".repeat(61);
        assertEquals(65, password.length());
        assertEquals(List.of(PasswordValidator.TOO_LONG), PasswordValidator.validate(password, USERNAME));
    }

    @Test
    void missingUppercaseIsRejected() {
        assertEquals(List.of(PasswordValidator.NO_UPPERCASE), PasswordValidator.validate("abcdef1!", USERNAME));
    }

    @Test
    void missingLowercaseIsRejected() {
        assertEquals(List.of(PasswordValidator.NO_LOWERCASE), PasswordValidator.validate("ABCDEF1!", USERNAME));
    }

    @Test
    void missingDigitIsRejected() {
        assertEquals(List.of(PasswordValidator.NO_DIGIT), PasswordValidator.validate("Abcdefg!", USERNAME));
    }

    @Test
    void missingSpecialCharacterIsRejected() {
        assertEquals(List.of(PasswordValidator.NO_SPECIAL), PasswordValidator.validate("Abcdefg1", USERNAME));
    }

    @Test
    void spaceIsRejected() {
        assertEquals(List.of(PasswordValidator.HAS_SPACE), PasswordValidator.validate("Abcd ef1!", USERNAME));
    }

    @Test
    void spaceDoesNotCountAsSpecialCharacter() {
        assertEquals(List.of(PasswordValidator.NO_SPECIAL, PasswordValidator.HAS_SPACE),
                PasswordValidator.validate("Abcd efg1", USERNAME));
    }

    @Test
    void passwordEqualToUsernameIsRejected() {
        assertEquals(List.of(PasswordValidator.SAME_AS_USERNAME), PasswordValidator.validate("Abcdef1!", "Abcdef1!"));
    }

    @Test
    void usernameComparisonIsCaseSensitive() {
        assertTrue(PasswordValidator.validate("Abcdef1!", "abcdef1!").isEmpty());
    }

    @Test
    void nullUsernameIsAllowed() {
        assertTrue(PasswordValidator.validate("Abcdef1!", null).isEmpty());
    }

    @Test
    void everyBrokenRuleIsReported() {
        assertEquals(List.of(PasswordValidator.TOO_SHORT, PasswordValidator.NO_UPPERCASE,
                        PasswordValidator.NO_DIGIT, PasswordValidator.NO_SPECIAL),
                PasswordValidator.validate("abc", USERNAME));
    }
}
