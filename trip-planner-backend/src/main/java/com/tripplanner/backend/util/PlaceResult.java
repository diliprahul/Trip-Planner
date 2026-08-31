package com.tripplanner.backend.util;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceResult {
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private java.util.Map<String, String> tags;
}
