package com.tripplanner.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String password;

    @Builder.Default
    @Column(nullable = false)
    private String provider = "LOCAL";

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "google_picture_url", length = 512)
    private String googlePictureUrl;

    @Column(name = "custom_picture", length = 255)
    private String customPicture;

    @Column(name = "city_of_residence", length = 100)
    private String cityOfResidence;

    @Column(name = "favourite_place", length = 100)
    private String favouritePlace;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
