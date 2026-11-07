package com.tripplanner.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "geocoded_cities")
@Getter @Setter @NoArgsConstructor
public class CityCoordinateCache {
    @Id
    @Column(length = 255)
    private String normalizedCity;
    private Double latitude;
    private Double longitude;
    private Instant resolvedAt;

    public CityCoordinateCache(String normalizedCity, Double latitude, Double longitude, Instant resolvedAt) {
        this.normalizedCity = normalizedCity;
        this.latitude = latitude;
        this.longitude = longitude;
        this.resolvedAt = resolvedAt;
    }
}
