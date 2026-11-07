package com.tripplanner.backend.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NominatimRateLimiter {
    private final NominatimRateLimitReservationService reservations;

    @PostConstruct
    public void initialize() {
        try { reservations.createStateIfAbsent(); }
        catch (org.springframework.dao.DataIntegrityViolationException concurrentInitialization) {
            // A second application instance created the singleton row first.
        }
    }

    public void awaitTurn() {
        final long slot;
        try { slot = reservations.reserveSlot(); }
        catch (RuntimeException e) { throw new IllegalStateException("Unable to coordinate Nominatim request rate", e); }
        long wait = slot - System.currentTimeMillis();
        if (wait > 0) {
            try { Thread.sleep(wait); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Nominatim request slot", e);
            }
        }
    }

    public void deferUntil(long timestamp) {
        try { reservations.deferUntil(timestamp); }
        catch (RuntimeException e) { throw new IllegalStateException("Unable to coordinate Nominatim request rate", e); }
    }
}
