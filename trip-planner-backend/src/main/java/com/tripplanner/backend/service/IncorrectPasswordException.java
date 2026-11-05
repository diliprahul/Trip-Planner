package com.tripplanner.backend.service;

/** Raised only when an authenticated local user fails the deletion password check. */
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("Incorrect password. Please enter your correct password.");
    }
}
