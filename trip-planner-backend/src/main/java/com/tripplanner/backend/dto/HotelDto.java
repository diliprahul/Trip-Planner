package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class HotelDto {

    private String name;
    private String address;

    private Double latitude;
    private Double longitude;

    // Used when no hotels are available
    private String searchUrl;
}
