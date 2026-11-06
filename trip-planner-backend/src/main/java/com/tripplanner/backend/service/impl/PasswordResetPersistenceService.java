package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.PasswordResetToken;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.PasswordResetRequestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetPersistenceService {
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;

    @Transactional(readOnly = true)
    public ResetRequestState requestState(String email, LocalDateTime now) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) return new ResetRequestState(PasswordResetRequestResult.EMAIL_NOT_FOUND, null);
        if (user.get().getProvider() != null && !"LOCAL".equalsIgnoreCase(user.get().getProvider())) {
            return new ResetRequestState(PasswordResetRequestResult.UNSUPPORTED_ACCOUNT, null);
        }
        Optional<PasswordResetToken> token = tokenRepository.findByEmail(email);
        if (token.isPresent() && token.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            return new ResetRequestState(PasswordResetRequestResult.COOLDOWN_ACTIVE,
                    new ResetRequestSnapshot(token.get().getCreatedAt()));
        }
        return new ResetRequestState(PasswordResetRequestResult.SENT,
                token.map(existing -> new ResetRequestSnapshot(existing.getCreatedAt())).orElse(null));
    }

    @Transactional
    public PasswordResetRequestResult persistToken(PasswordResetToken candidate, ResetRequestSnapshot expected,
                                                    LocalDateTime now) {
        Optional<User> user = userRepository.findByEmail(candidate.getEmail());
        if (user.isEmpty()) return PasswordResetRequestResult.EMAIL_NOT_FOUND;
        if (user.get().getProvider() != null && !"LOCAL".equalsIgnoreCase(user.get().getProvider())) {
            return PasswordResetRequestResult.UNSUPPORTED_ACCOUNT;
        }
        Optional<PasswordResetToken> current = tokenRepository.findByEmail(candidate.getEmail());
        if (current.isPresent()) {
            PasswordResetToken existing = current.get();
            if (existing.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
                return PasswordResetRequestResult.COOLDOWN_ACTIVE;
            }
            if (expected == null || !existing.getCreatedAt().equals(expected.createdAt())) {
                return PasswordResetRequestResult.COOLDOWN_ACTIVE;
            }
            tokenRepository.delete(existing);
            tokenRepository.flush();
        } else if (expected != null) {
            return PasswordResetRequestResult.COOLDOWN_ACTIVE;
        }
        tokenRepository.saveAndFlush(candidate);
        return PasswordResetRequestResult.SENT;
    }

    public record ResetRequestState(PasswordResetRequestResult result, ResetRequestSnapshot snapshot) {}
    public record ResetRequestSnapshot(LocalDateTime createdAt) {}
}
