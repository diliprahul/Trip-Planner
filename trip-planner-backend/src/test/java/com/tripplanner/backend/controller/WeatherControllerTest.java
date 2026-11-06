package com.tripplanner.backend.controller;

import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WeatherControllerTest {
    private TripRepository tripRepository;
    private WeatherService weatherService;
    private WeatherController controller;

    void setUp() {
        tripRepository = mock(TripRepository.class);
        weatherService = mock(WeatherService.class);
        controller = new WeatherController(tripRepository, weatherService);
    }

    @Test
    void returnsForecastIndependentlyForTripOwner() {
        setUp();
        TripRepository.TripWeatherData trip = mock(TripRepository.TripWeatherData.class);
        when(trip.getLatitude()).thenReturn(12.9716);
        when(trip.getLongitude()).thenReturn(77.5946);
        when(trip.getStartDate()).thenReturn(LocalDate.of(2026, 11, 10));
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 11, 12));
        when(tripRepository.findWeatherDataByTripIdAndOwnerEmail(9L, "owner@example.com"))
                .thenReturn(Optional.of(trip));
        when(weatherService.getForecast(12.9716, 77.5946, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)))
                .thenReturn(WeatherResponseDto.builder()
                        .status(WeatherResponseDto.Status.API_ERROR)
                        .message("Weather information could not be loaded at the moment.")
                        .build());

        ResponseEntity<WeatherResponseDto> response = controller.getTripWeather(9L, authentication("owner@example.com"));
        assertEquals(200, response.getStatusCode().value());
        assertSame(WeatherResponseDto.Status.API_ERROR, response.getBody().getStatus());

        verify(weatherService).getForecast(12.9716, 77.5946,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
    }

    @Test
    void doesNotCallWeatherProviderForUnknownOrOtherUsersTrip() {
        setUp();
        when(tripRepository.findWeatherDataByTripIdAndOwnerEmail(9L, "other@example.com"))
                .thenReturn(Optional.empty());

        ResponseEntity<WeatherResponseDto> response = controller.getTripWeather(9L, authentication("other@example.com"));
        assertEquals(404, response.getStatusCode().value());

        verifyNoInteractions(weatherService);
    }

    private Authentication authentication(String name) {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                name, "n/a", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
    }
}
