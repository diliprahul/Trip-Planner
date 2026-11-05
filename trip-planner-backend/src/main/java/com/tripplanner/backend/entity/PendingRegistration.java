package com.tripplanner.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_registrations", indexes = @Index(name = "idx_pending_registration_expiry", columnList = "expiry_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Column(name = "city_of_residence", length = 100)
    private String cityOfResidence;

    @Column(name = "favourite_place", length = 100)
    private String favouritePlace;

    @Column(nullable = false, name = "otp_hash")
    private String otpHash;

    @Column(nullable = false, name = "expiry_date")
    private LocalDateTime expiryDate;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false, name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastSentAt == null) lastSentAt = now;
    }
}
