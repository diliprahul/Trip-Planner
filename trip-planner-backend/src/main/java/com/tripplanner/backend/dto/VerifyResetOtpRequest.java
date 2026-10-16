package com.tripplanner.backend.dto;

import lombok.Data;

@Data
public class VerifyResetOtpRequest {
    private String email;
    private String otp;
}
