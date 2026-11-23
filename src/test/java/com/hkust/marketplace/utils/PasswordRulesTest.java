package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.PasswordRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordRulesTest {

    private static final String VALID = "Str0ng!Pass";

    @Test
    void validPasswordPassesEveryRule() {
        assertTrue(UserStore.checkPasswordStrength(VALID, "alice").isEmpty());
    }

    @Test
    void tooShortFailsLengthOnly() {
        assertEquals(Set.of(PasswordRule.LENGTH), UserStore.checkPasswordStrength("Ab1!", "alice"));
    }

    @Test
    void tooLongFailsLengthOnly() {
        String tooLong = "Aa1!" + "x".repeat(61);
        assertEquals(65, tooLong.length());
        assertEquals(Set.of(PasswordRule.LENGTH), UserStore.checkPasswordStrength(tooLong, "alice"));
    }

    @Test
    void missingUppercaseFailsUppercaseOnly() {
        assertEquals(Set.of(PasswordRule.UPPERCASE), UserStore.checkPasswordStrength("str0ng!pass", "alice"));
    }

    @Test
    void missingLowercaseFailsLowercaseOnly() {
        assertEquals(Set.of(PasswordRule.LOWERCASE), UserStore.checkPasswordStrength("STR0NG!PASS", "alice"));
    }

    @Test
    void missingDigitFailsDigitOnly() {
        assertEquals(Set.of(PasswordRule.DIGIT), UserStore.checkPasswordStrength("Strong!Password", "alice"));
    }

    @Test
    void missingSpecialCharacterFailsSpecialOnly() {
        assertEquals(Set.of(PasswordRule.SPECIAL), UserStore.checkPasswordStrength("Str0ngPass", "alice"));
    }

    @Test
    void spacesFailNoSpacesOnly() {
        assertEquals(Set.of(PasswordRule.NO_SPACES), UserStore.checkPasswordStrength("Str0ng! Pa55", "alice"));
    }

    @Test
    void passwordEqualToUsernameFailsNotUsernameOnly() {
        assertEquals(Set.of(PasswordRule.NOT_USERNAME),
                UserStore.checkPasswordStrength("Alice1!Password", "alice1!password"));
    }

    @Test
    void allBrokenRulesAreReportedTogether() {
        Set<PasswordRule> failed = UserStore.checkPasswordStrength("abc", "abc");
        assertTrue(failed.containsAll(Set.of(PasswordRule.LENGTH, PasswordRule.UPPERCASE,
                PasswordRule.DIGIT, PasswordRule.SPECIAL, PasswordRule.NOT_USERNAME)));
        assertFalse(failed.contains(PasswordRule.NO_SPACES));
    }

    @Test
    void nullPasswordIsRejected() {
        assertFalse(UserStore.checkPasswordStrength(null, "alice").isEmpty());
    }

    @Test
    void formattedErrorsNameEveryFailedRule() {
        String message = UserStore.formatPasswordErrors(UserStore.checkPasswordStrength("abc", "abc"));
        assertTrue(message.contains(PasswordRule.LENGTH.getMessage()));
        assertTrue(message.contains(PasswordRule.UPPERCASE.getMessage()));
        assertTrue(message.contains(PasswordRule.NOT_USERNAME.getMessage()));
    }
}
