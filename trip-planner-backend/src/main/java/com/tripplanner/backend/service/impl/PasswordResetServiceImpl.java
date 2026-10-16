package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.PasswordResetToken;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Optional<JavaMailSender> javaMailSender;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Generic response behavior: return silently without error to prevent email enumeration
            log.info("Password reset requested for unregistered email: {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        // Do not allow Google-only accounts to reset local password unless supported
        if (user.getProvider() != null && !"LOCAL".equalsIgnoreCase(user.getProvider())) {
            log.warn("Password reset attempted for non-local account: {} (provider: {})", normalizedEmail, user.getProvider());
            return;
        }

        // Check for existing token & resend cooldown
        Optional<PasswordResetToken> existingTokenOpt = passwordResetTokenRepository.findByEmail(normalizedEmail);
        if (existingTokenOpt.isPresent()) {
            PasswordResetToken existingToken = existingTokenOpt.get();
            if (existingToken.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                log.info("Password reset requested too quickly for email: {}", normalizedEmail);
                return; // Cooldown active, fail silently for security
            }
            passwordResetTokenRepository.deleteByEmail(normalizedEmail);
        }

        // Generate 6-digit secure OTP
        int otpVal = 100000 + secureRandom.nextInt(900000);
        String rawOtp = String.valueOf(otpVal);
        String otpHash = passwordEncoder.encode(rawOtp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(normalizedEmail)
                .otpHash(otpHash)
                .expiryDate(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .attempts(0)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Send Email
        sendOtpEmail(normalizedEmail, rawOtp);
    }

    @Override
    @Transactional
    public boolean verifyResetOtp(String email, String otp) {
        if (email == null || otp == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByEmail(normalizedEmail);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken token = tokenOpt.get();
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(token);
            return false;
        }

        if (token.getAttempts() >= MAX_ATTEMPTS) {
            passwordResetTokenRepository.delete(token);
            return false;
        }

        if (!passwordEncoder.matches(otp, token.getOtpHash())) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            return false;
        }

        return true;
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        if (email == null || otp == null || newPassword == null) {
            throw new IllegalArgumentException("Invalid password reset parameters");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByEmail(normalizedEmail);
        if (tokenOpt.isEmpty()) {
            throw new IllegalStateException("Invalid or expired password reset request");
        }

        PasswordResetToken token = tokenOpt.get();
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(token);
            throw new IllegalStateException("Password reset OTP has expired");
        }

        if (token.getAttempts() >= MAX_ATTEMPTS) {
            passwordResetTokenRepository.delete(token);
            throw new IllegalStateException("Maximum password reset attempts exceeded");
        }

        if (!passwordEncoder.matches(otp, token.getOtpHash())) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            throw new IllegalArgumentException("Invalid OTP");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getProvider() != null && !"LOCAL".equalsIgnoreCase(user.getProvider())) {
            throw new IllegalStateException("Google-only accounts cannot reset local password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate token after success
        passwordResetTokenRepository.deleteByEmail(normalizedEmail);
        log.info("Password successfully reset for user: {}", normalizedEmail);
    }

    private void sendOtpEmail(String email, String otp) {
        if (javaMailSender.isEmpty()) {
            log.warn("JavaMailSender is not configured. Password reset email cannot be sent for {}", email);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("TripPlanner Password Reset OTP");
            message.setText("Your TripPlanner password reset verification code is: " + otp + "\nThis code will expire in " + OTP_EXPIRY_MINUTES + " minutes.\nIf you did not request this, please ignore this email.");
            javaMailSender.get().send(message);
            log.info("Password reset OTP email sent successfully to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset OTP email to {}: {}", email, e.getMessage());
        }
    }
}
