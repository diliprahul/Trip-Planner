package com.tripplanner.backend.util;

import java.util.regex.Pattern;

public final class PasswordValidator {
    private static final Pattern VALID_PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{6,}$");
    public static final String MESSAGE = "Password must be at least 6 characters and include a letter and a number";

    private PasswordValidator() { }

    public static boolean isValid(String password) {
        return password != null && VALID_PASSWORD.matcher(password).matches();
    }
}
