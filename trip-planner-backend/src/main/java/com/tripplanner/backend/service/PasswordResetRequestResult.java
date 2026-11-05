package com.tripplanner.backend.service;

public enum PasswordResetRequestResult {
    SENT,
    EMAIL_NOT_FOUND,
    UNSUPPORTED_ACCOUNT,
    COOLDOWN_ACTIVE,
    DELIVERY_FAILED
}
