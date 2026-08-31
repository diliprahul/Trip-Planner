package com.tripplanner.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "day_plans")
public class DayPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Trip trip;

    private Integer dayNumber;
    private String placeName;
    private String description;

    private Double latitude;
    private Double longitude;

    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
