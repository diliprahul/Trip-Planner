package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.GeocodingRateLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GeocodingRateLimitRepository extends JpaRepository<GeocodingRateLimit, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GeocodingRateLimit r where r.id = :id")
    Optional<GeocodingRateLimit> lockById(@Param("id") Integer id);
}
