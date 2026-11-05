package com.tripplanner.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {
    @Test
    void acceptsPasswordsWithAtLeastSixCharactersALetterAndADigit() {
        assertTrue(PasswordValidator.isValid("rahul123"));
        assertTrue(PasswordValidator.isValid("rahul1"));
        assertTrue(PasswordValidator.isValid("abc123"));
    }

    @Test
    void rejectsPasswordsWithoutBothRequiredCharacterTypesOrEnoughLength() {
        assertFalse(PasswordValidator.isValid("123456"));
        assertFalse(PasswordValidator.isValid("abcdef"));
        assertFalse(PasswordValidator.isValid("abc12"));
    }
}
