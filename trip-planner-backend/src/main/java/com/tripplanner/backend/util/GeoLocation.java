package com.tripplanner.backend.util;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoLocation {
    private Double latitude;
    private Double longitude;
}
