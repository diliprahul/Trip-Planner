package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.WeatherResponseDto;
import java.time.LocalDate;

public interface WeatherService {
    WeatherResponseDto getForecast(Double lat, Double lon, LocalDate startDate, LocalDate endDate);
}
