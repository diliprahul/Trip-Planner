package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.GeocodingRateLimit;
import com.tripplanner.backend.repository.GeocodingRateLimitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NominatimRateLimiterTest {
    @Autowired NominatimRateLimiter limiter;
    @Autowired GeocodingRateLimitRepository repository;

    @Test
    void concurrentInitializationLeavesExactlyOneSharedRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(limiter::initialize));
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
            assertTrue(repository.existsById(1));
            assertEquals(1, repository.count());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void reservationsFromConcurrentCallsAreSerializedAndWaitOutsideTransaction() throws Exception {
        long initialSlot = System.currentTimeMillis() + 1_500;
        repository.saveAndFlush(new GeocodingRateLimit(1, initialSlot));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Callable<Long> reserve = () -> {
                ready.countDown();
                ready.await();
                long before = System.currentTimeMillis();
                limiter.awaitTurn();
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return System.currentTimeMillis() - before;
            };
            Future<Long> first = pool.submit(reserve);
            Future<Long> second = pool.submit(reserve);
            long a = first.get(10, TimeUnit.SECONDS), b = second.get(10, TimeUnit.SECONDS);
            assertTrue(Math.min(a, b) >= 1_000,
                    () -> "the first reservation must honor the existing global slot: a=" + a + ", b=" + b);
            assertTrue(Math.max(a, b) >= 2_000,
                    () -> "the second concurrent reservation must receive a later global slot: a=" + a + ", b=" + b);
            assertTrue(repository.findById(1).orElseThrow().getNextAllowedAt() >= initialSlot + 2_000,
                    "both reservations must be committed to the shared database state");
        } finally { pool.shutdownNow(); }
    }
}
