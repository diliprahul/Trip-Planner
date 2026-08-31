package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WeatherResponseDto {
    public enum Status {
        AVAILABLE,
        PARTIALLY_AVAILABLE,
        NOT_AVAILABLE_YET,
        API_ERROR
    }

    private Status status;
    private String message;
    private List<WeatherForecastDto> forecasts;
}
