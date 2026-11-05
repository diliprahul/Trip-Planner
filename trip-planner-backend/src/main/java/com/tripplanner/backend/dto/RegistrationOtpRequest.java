package com.tripplanner.backend.dto;

import lombok.Data;

@Data
public class RegistrationOtpRequest {
    private String email;
    private String otp;
}
