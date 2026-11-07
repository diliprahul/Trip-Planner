package com.tripplanner.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "geocoding_rate_limit")
@Getter @Setter @NoArgsConstructor
public class GeocodingRateLimit {
    @Id
    private Integer id;
    @Column(nullable = false)
    private long nextAllowedAt;

    public GeocodingRateLimit(Integer id, long nextAllowedAt) {
        this.id = id;
        this.nextAllowedAt = nextAllowedAt;
    }
}
