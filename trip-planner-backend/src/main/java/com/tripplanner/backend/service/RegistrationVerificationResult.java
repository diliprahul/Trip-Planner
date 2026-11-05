package com.tripplanner.backend.service;

public enum RegistrationVerificationResult {
    SENT,
    PENDING_EXISTS,
    EMAIL_REGISTERED,
    COOLDOWN_ACTIVE,
    DELIVERY_FAILED
}
