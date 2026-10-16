package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.ForgotPasswordRequest;
import com.tripplanner.backend.dto.ResetPasswordRequest;
import com.tripplanner.backend.dto.VerifyResetOtpRequest;
import com.tripplanner.backend.entity.PasswordResetToken;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testGenericForgotPasswordResponseKnownAndUnknown() throws Exception {
        // Unknown email
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("unknown@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Create local user
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .password(passwordEncoder.encode("oldpassword"))
                .provider("LOCAL")
                .build();
        userRepository.save(user);

        // Known email
        req.setEmail("test@example.com");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify token was generated in DB
        assertTrue(passwordResetTokenRepository.findByEmail("test@example.com").isPresent());
    }

    @Test
    void testOtpVerificationAndResetFlow() throws Exception {
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .password(passwordEncoder.encode("oldpassword"))
                .provider("LOCAL")
                .build();
        userRepository.save(user);

        // Request reset
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("test@example.com");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        PasswordResetToken token = passwordResetTokenRepository.findByEmail("test@example.com").orElseThrow();

        // Test incorrect OTP first against the generated one.
        VerifyResetOtpRequest verifyReq = new VerifyResetOtpRequest();
        verifyReq.setEmail("test@example.com");
        verifyReq.setOtp("000000"); // likely incorrect

        mockMvc.perform(post("/api/auth/verify-reset-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest());

        // Now update token with known OTP hash for "123456"
        token.setOtpHash(passwordEncoder.encode("123456"));
        token.setAttempts(0);
        passwordResetTokenRepository.save(token);

        // Verify with correct OTP
        verifyReq.setOtp("123456");
        mockMvc.perform(post("/api/auth/verify-reset-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // Test invalid password rejection (< 6 chars)
        ResetPasswordRequest resetReq = new ResetPasswordRequest();
        resetReq.setEmail("test@example.com");
        resetReq.setOtp("123456");
        resetReq.setNewPassword("123"); // too short

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().is4xxClientError());

        // Successful password reset
        resetReq.setNewPassword("newsecurepassword123");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // Verify password updated in DB with BCrypt
        User updatedUser = userRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(passwordEncoder.matches("newsecurepassword123", updatedUser.getPassword()));

        // Verify token invalidated (replay rejection)
        assertFalse(passwordResetTokenRepository.findByEmail("test@example.com").isPresent());
    }

    @Test
    void testGoogleOnlyAccountProtection() throws Exception {
        User googleUser = User.builder()
                .name("Google User")
                .email("google@example.com")
                .password(passwordEncoder.encode("dummy"))
                .provider("GOOGLE")
                .providerId("goog-123")
                .build();
        userRepository.save(googleUser);

        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("google@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Should not create token for google-only account
        assertFalse(passwordResetTokenRepository.findByEmail("google@example.com").isPresent());
    }

    @Test
    void testOtpAttemptLimitAndExpiration() throws Exception {
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .password(passwordEncoder.encode("oldpassword"))
                .provider("LOCAL")
                .build();
        userRepository.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .email("test@example.com")
                .otpHash(passwordEncoder.encode("123456"))
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .attempts(0)
                .build();
        passwordResetTokenRepository.save(token);

        VerifyResetOtpRequest verifyReq = new VerifyResetOtpRequest();
        verifyReq.setEmail("test@example.com");
        verifyReq.setOtp("wrong1");

        // 3 failed attempts
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/verify-reset-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyReq)))
                    .andExpect(status().isBadRequest());
        }

        // 4th attempt with correct OTP should fail because max attempts exceeded
        verifyReq.setOtp("123456");
        mockMvc.perform(post("/api/auth/verify-reset-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEmailSendFailureSwallowedGracefully() throws Exception {
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .password(passwordEncoder.encode("oldpassword"))
                .provider("LOCAL")
                .build();
        userRepository.save(user);

        org.mockito.Mockito.doThrow(new org.springframework.mail.MailException("SMTP failure") {})
                .when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("test@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify token was saved even though email send failed
        assertTrue(passwordResetTokenRepository.findByEmail("test@example.com").isPresent());
    }
}
