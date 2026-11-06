package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.PasswordResetToken;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.PasswordResetService;
import com.tripplanner.backend.service.PasswordResetRequestResult;
import com.tripplanner.backend.service.EmailDeliveryService;
import com.tripplanner.backend.service.impl.PasswordResetPersistenceService.ResetRequestState;
import com.tripplanner.backend.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService emailDeliveryService;
    private final PasswordResetPersistenceService persistenceService;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;
    private static final ReentrantLock[] EMAIL_LOCKS = createLocks();

    @Override
    public PasswordResetRequestResult requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return PasswordResetRequestResult.EMAIL_NOT_FOUND;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        ReentrantLock lock = EMAIL_LOCKS[Math.floorMod(normalizedEmail.hashCode(), EMAIL_LOCKS.length)];
        lock.lock();
        try {
            return requestPasswordResetLocked(normalizedEmail);
        } finally {
            lock.unlock();
        }
    }

    private PasswordResetRequestResult requestPasswordResetLocked(String normalizedEmail) {
        LocalDateTime requestedAt = LocalDateTime.now();
        ResetRequestState state;
        try {
            state = persistenceService.requestState(normalizedEmail, requestedAt);
        } catch (RuntimeException exception) {
            log.error("password reset lookup failed; exceptionType={}", exception.getClass().getSimpleName());
            return PasswordResetRequestResult.DELIVERY_FAILED;
        }
        if (state.result() != PasswordResetRequestResult.SENT) return state.result();

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

        if (!sendOtpEmail(normalizedEmail, rawOtp)) {
            return PasswordResetRequestResult.DELIVERY_FAILED;
        }

        try {
            PasswordResetRequestResult persisted = persistenceService.persistToken(
                    resetToken, state.snapshot(), LocalDateTime.now());
            return persisted == PasswordResetRequestResult.SENT
                    ? PasswordResetRequestResult.SENT : PasswordResetRequestResult.DELIVERY_FAILED;
        } catch (RuntimeException exception) {
            log.error("password reset persistence failed after email delivery; exceptionType={}",
                    exception.getClass().getSimpleName());
            return PasswordResetRequestResult.DELIVERY_FAILED;
        }
    }

    @Override
    @Transactional
    public boolean verifyResetOtp(String email, String otp) {
        if (email == null || otp == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
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
            if (token.getAttempts() >= MAX_ATTEMPTS) {
                passwordResetTokenRepository.delete(token);
            } else {
                passwordResetTokenRepository.save(token);
            }
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
        if (!PasswordValidator.isValid(newPassword)) {
            throw new IllegalArgumentException(PasswordValidator.MESSAGE);
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
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
            if (token.getAttempts() >= MAX_ATTEMPTS) {
                passwordResetTokenRepository.delete(token);
            } else {
                passwordResetTokenRepository.save(token);
            }
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
        log.info("Password reset completed successfully");
    }

    private boolean sendOtpEmail(String email, String otp) {
        try {
            return emailDeliveryService.send(email, "TripPlanner Password Reset OTP",
                    "Your TripPlanner password reset verification code is: " + otp + "\nThis code will expire in " + OTP_EXPIRY_MINUTES + " minutes.\nIf you did not request this, please ignore this email.");
        } catch (RuntimeException exception) {
            log.error("password reset email delivery failed; exceptionType={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[128];
        Arrays.setAll(locks, ignored -> new ReentrantLock());
        return locks;
    }
}
