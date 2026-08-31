package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.WeatherForecastDto;
import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate restTemplate;
    private final String apiKey;

    public WeatherServiceImpl(RestTemplate restTemplate, @Value("${weather.openweather.api-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Override
    public WeatherResponseDto getForecast(Double lat, Double lon, LocalDate startDate, LocalDate endDate) {
        try {
            // Using 5 day / 3 hour forecast
            String url = String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric", lat, lon, apiKey);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response == null || !response.containsKey("list")) {
                return WeatherResponseDto.builder()
                        .status(WeatherResponseDto.Status.API_ERROR)
                        .message("Weather service is currently unreachable.")
                        .build();
            }

            List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
            
            // Group by day
            Map<LocalDate, List<Map<String, Object>>> groupedByDay = list.stream()
                    .collect(Collectors.groupingBy(item -> 
                        Instant.ofEpochSecond(((Number) item.get("dt")).longValue())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()));

            List<WeatherForecastDto> forecasts = new ArrayList<>();
            List<LocalDate> missingDates = new ArrayList<>();
            
            // Filter by date range
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                if (!groupedByDay.containsKey(date)) {
                    missingDates.add(date);
                    continue;
                }
                
                List<Map<String, Object>> dailyItems = groupedByDay.get(date);
                
                double minTemp = dailyItems.stream().mapToDouble(i -> ((Map<String, Object>) i.get("main")).get("temp_min") != null ? ((Number) ((Map<String, Object>) i.get("main")).get("temp_min")).doubleValue() : 0).min().orElse(0);
                double maxTemp = dailyItems.stream().mapToDouble(i -> ((Map<String, Object>) i.get("main")).get("temp_max") != null ? ((Number) ((Map<String, Object>) i.get("main")).get("temp_max")).doubleValue() : 0).max().orElse(0);
                
                Map<String, Object> firstItem = dailyItems.get(0);
                List<Map<String, Object>> weather = (List<Map<String, Object>>) firstItem.get("weather");
                String condition = weather != null && !weather.isEmpty() ? (String) weather.get(0).get("main") : "Unknown";
                String icon = weather != null && !weather.isEmpty() ? (String) weather.get(0).get("icon") : "";
                
                Double rain = 0.0;
                if (firstItem.containsKey("rain") && firstItem.get("rain") instanceof Map) {
                    Map<String, Object> rainMap = (Map<String, Object>) firstItem.get("rain");
                    if (rainMap.containsKey("3h")) {
                        rain = ((Number) rainMap.get("3h")).doubleValue();
                    }
                }

                forecasts.add(WeatherForecastDto.builder()
                        .date(date.toString())
                        .condition(condition)
                        .icon(icon)
                        .minTemperature(minTemp)
                        .maxTemperature(maxTemp)
                        .rain(rain)
                        .build());
            }

            WeatherResponseDto.Status status;
            String message = null;

            if (forecasts.isEmpty()) {
                status = WeatherResponseDto.Status.NOT_AVAILABLE_YET;
                message = "Detailed weather forecasts are not available yet for your selected travel dates. Weather forecasts will become available closer to your trip.";
            } else if (!missingDates.isEmpty()) {
                status = WeatherResponseDto.Status.PARTIALLY_AVAILABLE;
                message = "Some dates are outside the available forecast range.";
            } else {
                status = WeatherResponseDto.Status.AVAILABLE;
            }

            return WeatherResponseDto.builder()
                    .status(status)
                    .message(message)
                    .forecasts(forecasts)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to fetch weather", e);
            return WeatherResponseDto.builder()
                    .status(WeatherResponseDto.Status.API_ERROR)
                    .message("Weather information could not be loaded at the moment.")
                    .build();
        }
    }
}
