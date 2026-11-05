package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByEmail(String email);
    void deleteByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM pending_registrations WHERE expiry_date <= CURRENT_TIMESTAMP", nativeQuery = true)
    int deleteExpiredRegistrations();
}
