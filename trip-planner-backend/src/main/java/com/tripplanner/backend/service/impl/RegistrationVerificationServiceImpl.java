package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.PendingRegistration;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.service.EmailDeliveryService;
import com.tripplanner.backend.service.RegistrationVerificationResult;
import com.tripplanner.backend.service.RegistrationVerificationService;
import com.tripplanner.backend.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationVerificationServiceImpl implements RegistrationVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int EXPIRY_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final ReentrantLock[] EMAIL_LOCKS = createLocks();

    private final RegistrationPersistenceService persistence;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService emailDeliveryService;

    @Override
    public RegistrationVerificationResult requestRegistration(RegisterRequest request) {
        validateRequest(request);
        String email = normalizeEmail(request.getEmail());
        ReentrantLock lock = lockFor(email);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            RegistrationVerificationResult status;
            try {
                status = persistence.registrationStatus(email, now);
            } catch (RuntimeException exception) {
                log.error("registration lookup failed; exceptionType={}", exception.getClass().getSimpleName());
                return RegistrationVerificationResult.DELIVERY_FAILED;
            }
            if (status != RegistrationVerificationResult.SENT) return status;

            String otp = generateOtp();
            PendingRegistration pending = PendingRegistration.builder()
                    .email(email).name(request.getName().trim())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .cityOfResidence(trimToNull(request.getCityOfResidence()))
                    .favouritePlace(trimToNull(request.getFavouritePlace()))
                    .otpHash(passwordEncoder.encode(otp))
                    .expiryDate(now.plusMinutes(EXPIRY_MINUTES))
                    .attempts(0).lastSentAt(now).build();

            if (!sendOtp(email, otp)) return RegistrationVerificationResult.DELIVERY_FAILED;
            try {
                return persistence.persistNewRegistration(pending, LocalDateTime.now());
            } catch (RuntimeException exception) {
                log.error("registration persistence failed after email delivery; exceptionType={}",
                        exception.getClass().getSimpleName());
                try {
                    RegistrationVerificationResult current = persistence.registrationStatus(email, LocalDateTime.now());
                    if (current != RegistrationVerificationResult.SENT) return current;
                } catch (RuntimeException lookupException) {
                    log.error("registration recovery lookup failed; exceptionType={}",
                            lookupException.getClass().getSimpleName());
                }
                return RegistrationVerificationResult.DELIVERY_FAILED;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RegistrationVerificationResult resendOtp(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        ReentrantLock lock = lockFor(email);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            RegistrationPersistenceService.ResendState state;
            try {
                state = persistence.checkResend(email, now);
            } catch (RuntimeException exception) {
                log.error("registration resend lookup failed; exceptionType={}", exception.getClass().getSimpleName());
                return RegistrationVerificationResult.DELIVERY_FAILED;
            }
            switch (state.status()) {
                case NOT_FOUND -> throw new IllegalStateException("No pending registration was found for that email.");
                case EXPIRED -> throw new IllegalStateException("The verification code has expired. Please register again.");
                case COOLDOWN_ACTIVE -> { return RegistrationVerificationResult.COOLDOWN_ACTIVE; }
                case READY -> { }
                default -> throw new IllegalStateException("Unable to resend verification code.");
            }

            String otp = generateOtp();
            if (!sendOtp(email, otp)) return RegistrationVerificationResult.DELIVERY_FAILED;
            LocalDateTime sentAt = LocalDateTime.now();
            try {
                RegistrationPersistenceService.ResendStatus updated = persistence.persistResend(
                        email, state.snapshot(), passwordEncoder.encode(otp), sentAt,
                        sentAt.plusMinutes(EXPIRY_MINUTES));
                return switch (updated) {
                    case SENT -> RegistrationVerificationResult.SENT;
                    case COOLDOWN_ACTIVE -> RegistrationVerificationResult.COOLDOWN_ACTIVE;
                    case NOT_FOUND, EXPIRED, READY -> RegistrationVerificationResult.DELIVERY_FAILED;
                };
            } catch (RuntimeException exception) {
                log.error("registration resend persistence failed after email delivery; exceptionType={}",
                        exception.getClass().getSimpleName());
                return RegistrationVerificationResult.DELIVERY_FAILED;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public User verifyOtp(String rawEmail, String otp) {
        return persistence.verifyOtp(normalizeEmail(rawEmail), otp);
    }

    private void validateRequest(RegisterRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) throw new IllegalArgumentException("Name is required.");
        String email = normalizeEmail(request.getEmail());
        if (!EMAIL_PATTERN.matcher(email).matches()) throw new IllegalArgumentException("Please enter a valid email address.");
        if (!PasswordValidator.isValid(request.getPassword())) throw new IllegalArgumentException(PasswordValidator.MESSAGE + ".");
        if (request.getName().trim().length() > 100) throw new IllegalArgumentException("Name cannot exceed 100 characters.");
        if (request.getCityOfResidence() != null && request.getCityOfResidence().trim().length() > 100) throw new IllegalArgumentException("City of residence cannot exceed 100 characters.");
        if (request.getFavouritePlace() != null && request.getFavouritePlace().trim().length() > 100) throw new IllegalArgumentException("Favourite place or city cannot exceed 100 characters.");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String generateOtp() { return String.valueOf(100000 + RANDOM.nextInt(900000)); }

    private boolean sendOtp(String email, String otp) {
        try {
            return emailDeliveryService.send(email, "TripPlanner Email Verification Code",
                    "Your TripPlanner email verification code is: " + otp + "\nThis code expires in " + EXPIRY_MINUTES + " minutes.");
        } catch (RuntimeException exception) {
            log.error("registration email delivery failed; exceptionType={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[128];
        Arrays.setAll(locks, ignored -> new ReentrantLock());
        return locks;
    }

    private static ReentrantLock lockFor(String email) {
        return EMAIL_LOCKS[Math.floorMod(email.hashCode(), EMAIL_LOCKS.length)];
    }
}
