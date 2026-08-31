package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DayPlanDto {

    private Integer dayNumber;
    private String placeName;
    private String description;

    private Double latitude;
    private Double longitude;

    // Image (already present, kept)
    private String imageUrl;

    // ✅ Google Maps redirect link
    private String mapsUrl;
}
