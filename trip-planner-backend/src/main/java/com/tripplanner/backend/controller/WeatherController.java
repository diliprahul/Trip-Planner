package com.tripplanner.backend.controller;

import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class WeatherController {
    private final TripRepository tripRepository;
    private final WeatherService weatherService;

    @GetMapping("/{tripId}/weather")
    public ResponseEntity<WeatherResponseDto> getTripWeather(
            @PathVariable Long tripId,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var weatherData = tripRepository.findWeatherDataByTripIdAndOwnerEmail(tripId, authentication.getName());
        if (weatherData.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TripRepository.TripWeatherData trip = weatherData.get();
        LocalDate startDate = trip.getStartDate();
        LocalDate endDate = trip.getEndDate();
        return ResponseEntity.ok(weatherService.getForecast(
                trip.getLatitude(), trip.getLongitude(), startDate, endDate));
    }
}
