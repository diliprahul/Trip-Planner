package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.GeocodingRateLimit;
import com.tripplanner.backend.repository.GeocodingRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NominatimRateLimitReservationService {
    static final int SINGLETON_ID = 1;
    static final long INTERVAL_MS = 1_000L;
    private final GeocodingRateLimitRepository repository;

    @Transactional
    public void createStateIfAbsent() {
        if (!repository.existsById(SINGLETON_ID)) {
            repository.saveAndFlush(new GeocodingRateLimit(SINGLETON_ID, 0));
        }
    }

    @Transactional
    public long reserveSlot() {
        GeocodingRateLimit state = repository.lockById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Nominatim rate limit state is missing"));
        long slot = Math.max(System.currentTimeMillis(), state.getNextAllowedAt());
        state.setNextAllowedAt(slot + INTERVAL_MS);
        repository.saveAndFlush(state);
        return slot;
    }

    @Transactional
    public void deferUntil(long timestamp) {
        GeocodingRateLimit state = repository.lockById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Nominatim rate limit state is missing"));
        state.setNextAllowedAt(Math.max(state.getNextAllowedAt(), timestamp));
        repository.saveAndFlush(state);
    }
}
