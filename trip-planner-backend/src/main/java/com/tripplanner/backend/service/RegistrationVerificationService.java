package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.User;

public interface RegistrationVerificationService {
    RegistrationVerificationResult requestRegistration(RegisterRequest request);
    RegistrationVerificationResult resendOtp(String email);
    User verifyOtp(String email, String otp);
}
