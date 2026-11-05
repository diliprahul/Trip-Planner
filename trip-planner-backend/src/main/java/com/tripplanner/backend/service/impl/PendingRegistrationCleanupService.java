package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.repository.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PendingRegistrationCleanupService {
    private final PendingRegistrationRepository pendingRegistrationRepository;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void deleteExpiredRegistrations() {
        pendingRegistrationRepository.deleteExpiredRegistrations();
    }
}
