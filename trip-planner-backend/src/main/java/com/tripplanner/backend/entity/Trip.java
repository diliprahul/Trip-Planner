package com.tripplanner.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String origin;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer days;

    private Double latitude;
    private Double longitude;

    // ✅ USER SELECTED CATEGORIES
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_categories", joinColumns = @JoinColumn(name = "trip_id"))
    @Column(name = "category")
    private List<String> placeCategories = new ArrayList<>();

    public void setCategories(List<String> categories) {
        this.placeCategories = categories;
    }

    public List<String> getCategories() {
        return placeCategories;
    }

    public void setPlaceCategories(List<String> placeCategories) {
        this.placeCategories = placeCategories;
    }

    public List<String> getPlaceCategories() {
        return placeCategories != null ? placeCategories : java.util.Collections.emptyList();
    }

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
