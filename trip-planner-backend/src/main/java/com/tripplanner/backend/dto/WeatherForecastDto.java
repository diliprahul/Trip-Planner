package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeatherForecastDto {
    private String date;
    private String condition;
    private String icon;
    private Double minTemperature;
    private Double maxTemperature;
    private Double rain;
}
