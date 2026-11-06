package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.PendingRegistration;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.RegistrationVerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationPersistenceService {
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public RegistrationVerificationResult registrationStatus(String email, LocalDateTime now) {
        if (userRepository.existsByEmail(email)) return RegistrationVerificationResult.EMAIL_REGISTERED;
        Optional<PendingRegistration> pending = pendingRepository.findByEmail(email);
        return pending.filter(record -> record.getExpiryDate().isAfter(now)).isPresent()
                ? RegistrationVerificationResult.PENDING_EXISTS : RegistrationVerificationResult.SENT;
    }

    @Transactional
    public RegistrationVerificationResult persistNewRegistration(PendingRegistration candidate, LocalDateTime now) {
        if (userRepository.existsByEmail(candidate.getEmail())) return RegistrationVerificationResult.EMAIL_REGISTERED;
        Optional<PendingRegistration> existing = pendingRepository.findByEmail(candidate.getEmail());
        if (existing.isPresent()) {
            if (existing.get().getExpiryDate().isAfter(now)) return RegistrationVerificationResult.PENDING_EXISTS;
            pendingRepository.delete(existing.get());
            pendingRepository.flush();
        }
        pendingRepository.saveAndFlush(candidate);
        return RegistrationVerificationResult.SENT;
    }

    @Transactional
    public ResendState checkResend(String email, LocalDateTime now) {
        Optional<PendingRegistration> result = pendingRepository.findByEmail(email);
        if (result.isEmpty()) return new ResendState(ResendStatus.NOT_FOUND, null);
        PendingRegistration pending = result.get();
        if (!pending.getExpiryDate().isAfter(now)) {
            pendingRepository.delete(pending);
            return new ResendState(ResendStatus.EXPIRED, null);
        }
        if (pending.getLastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(now)) {
            return new ResendState(ResendStatus.COOLDOWN_ACTIVE, null);
        }
        return new ResendState(ResendStatus.READY,
                new ResendSnapshot(pending.getLastSentAt(), pending.getExpiryDate()));
    }

    @Transactional
    public ResendStatus persistResend(String email, ResendSnapshot expected, String otpHash,
                                      LocalDateTime now, LocalDateTime expiry) {
        Optional<PendingRegistration> result = pendingRepository.findByEmail(email);
        if (result.isEmpty()) return ResendStatus.NOT_FOUND;
        PendingRegistration pending = result.get();
        if (!pending.getLastSentAt().equals(expected.lastSentAt())) return ResendStatus.COOLDOWN_ACTIVE;
        pending.setOtpHash(otpHash);
        pending.setExpiryDate(expiry);
        pending.setAttempts(0);
        pending.setLastSentAt(now);
        pendingRepository.saveAndFlush(pending);
        return ResendStatus.SENT;
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public User verifyOtp(String email, String otp) {
        if (otp == null || !otp.matches("\\d{6}")) throw new IllegalArgumentException("Invalid verification code.");
        PendingRegistration pending = pendingRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Invalid or expired verification code."));
        if (!pending.getExpiryDate().isAfter(LocalDateTime.now())) {
            pendingRepository.delete(pending);
            throw new IllegalStateException("The verification code has expired. Please register again.");
        }
        if (!passwordEncoder.matches(otp, pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            if (pending.getAttempts() >= MAX_ATTEMPTS) pendingRepository.delete(pending);
            else pendingRepository.save(pending);
            if (pending.getAttempts() >= MAX_ATTEMPTS) {
                throw new IllegalStateException("Maximum verification attempts exceeded. Please register again.");
            }
            throw new IllegalArgumentException("Invalid verification code.");
        }
        if (userRepository.existsByEmail(email)) {
            pendingRepository.delete(pending);
            throw new IllegalStateException("Email is already registered.");
        }
        User saved = userRepository.save(User.builder().name(pending.getName()).email(pending.getEmail())
                .password(pending.getPasswordHash()).provider("LOCAL")
                .cityOfResidence(pending.getCityOfResidence()).favouritePlace(pending.getFavouritePlace()).build());
        pendingRepository.delete(pending);
        return saved;
    }

    public enum ResendStatus { READY, SENT, NOT_FOUND, EXPIRED, COOLDOWN_ACTIVE }
    public record ResendState(ResendStatus status, ResendSnapshot snapshot) {}
    public record ResendSnapshot(LocalDateTime lastSentAt, LocalDateTime expiryDate) {}
}
