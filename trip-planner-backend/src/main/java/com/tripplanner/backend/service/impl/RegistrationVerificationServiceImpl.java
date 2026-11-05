package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.PendingRegistration;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.RegistrationVerificationResult;
import com.tripplanner.backend.service.RegistrationVerificationService;
import com.tripplanner.backend.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationVerificationServiceImpl implements RegistrationVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int COOLDOWN_SECONDS = 60;

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Optional<JavaMailSender> javaMailSender;

    @Override
    @Transactional
    public RegistrationVerificationResult requestRegistration(RegisterRequest request) {
        validateRequest(request);
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) return RegistrationVerificationResult.EMAIL_REGISTERED;

        Optional<PendingRegistration> existing = pendingRegistrationRepository.findByEmail(email);
        PendingRegistration expiredPending = null;
        if (existing.isPresent()) {
            if (isExpired(existing.get())) {
                expiredPending = existing.get();
            } else {
                return RegistrationVerificationResult.PENDING_EXISTS;
            }
        }

        String otp = generateOtp();
        PendingRegistration pending = PendingRegistration.builder()
                .email(email).name(request.getName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .cityOfResidence(trimToNull(request.getCityOfResidence()))
                .favouritePlace(trimToNull(request.getFavouritePlace()))
                .otpHash(passwordEncoder.encode(otp))
                .expiryDate(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .attempts(0).lastSentAt(LocalDateTime.now()).build();

        if (!sendOtp(email, otp)) return RegistrationVerificationResult.DELIVERY_FAILED;
        if (expiredPending != null) {
            pendingRegistrationRepository.delete(expiredPending);
            pendingRegistrationRepository.flush();
        }
        pendingRegistrationRepository.save(pending);
        return RegistrationVerificationResult.SENT;
    }

    @Override
    @Transactional
    public RegistrationVerificationResult resendOtp(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Optional<PendingRegistration> result = pendingRegistrationRepository.findByEmail(email);
        if (result.isEmpty()) throw new IllegalStateException("No pending registration was found for that email.");
        PendingRegistration pending = result.get();
        if (isExpired(pending)) {
            pendingRegistrationRepository.delete(pending);
            throw new IllegalStateException("The verification code has expired. Please register again.");
        }
        if (pending.getLastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            return RegistrationVerificationResult.COOLDOWN_ACTIVE;
        }

        String otp = generateOtp();
        if (!sendOtp(email, otp)) return RegistrationVerificationResult.DELIVERY_FAILED;
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiryDate(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        pending.setAttempts(0);
        pending.setLastSentAt(LocalDateTime.now());
        pendingRegistrationRepository.save(pending);
        return RegistrationVerificationResult.SENT;
    }

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public User verifyOtp(String rawEmail, String otp) {
        String email = normalizeEmail(rawEmail);
        if (otp == null || !otp.matches("\\d{6}")) throw new IllegalArgumentException("Invalid verification code.");
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Invalid or expired verification code."));
        if (isExpired(pending)) {
            pendingRegistrationRepository.delete(pending);
            throw new IllegalStateException("The verification code has expired. Please register again.");
        }
        if (!passwordEncoder.matches(otp, pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            if (pending.getAttempts() >= MAX_ATTEMPTS) {
                pendingRegistrationRepository.delete(pending);
                throw new IllegalStateException("Maximum verification attempts exceeded. Please register again.");
            }
            pendingRegistrationRepository.save(pending);
            throw new IllegalArgumentException("Invalid verification code.");
        }
        if (userRepository.existsByEmail(email)) {
            pendingRegistrationRepository.delete(pending);
            throw new IllegalStateException("Email is already registered.");
        }

        User user = User.builder().name(pending.getName()).email(pending.getEmail())
                .password(pending.getPasswordHash()).provider("LOCAL")
                .cityOfResidence(pending.getCityOfResidence()).favouritePlace(pending.getFavouritePlace()).build();
        User saved = userRepository.save(user);
        pendingRegistrationRepository.delete(pending);
        return saved;
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
    private boolean isExpired(PendingRegistration pending) { return !pending.getExpiryDate().isAfter(LocalDateTime.now()); }
    private String generateOtp() { return String.valueOf(100000 + RANDOM.nextInt(900000)); }

    private boolean sendOtp(String email, String otp) {
        if (javaMailSender.isEmpty()) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("TripPlanner Email Verification Code");
            message.setText("Your TripPlanner email verification code is: " + otp + "\nThis code expires in " + EXPIRY_MINUTES + " minutes.");
            javaMailSender.get().send(message);
            return true;
        } catch (org.springframework.mail.MailException exception) {
            log.error("SMTP failed to accept registration verification email for {}. exceptionType={}, message={}",
                    email, exception.getClass().getName(), exception.getMessage(), exception);
            return false;
        }
    }
}
