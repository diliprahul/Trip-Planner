package com.tripplanner.backend.service;

public interface PasswordResetService {
    void requestPasswordReset(String email);
    boolean verifyResetOtp(String email, String otp);
    void resetPassword(String email, String otp, String newPassword);
}
