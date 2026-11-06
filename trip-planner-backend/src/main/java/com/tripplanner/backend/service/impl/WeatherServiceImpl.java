package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.WeatherForecastDto;
import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final Map<ForecastKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<ForecastKey, CompletableFuture<WeatherResponseDto>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    public WeatherServiceImpl(RestTemplate restTemplate) {
        this(restTemplate, Clock.systemUTC());
    }

    public WeatherServiceImpl(RestTemplate restTemplate, Clock clock) {
        this.restTemplate = restTemplate;
        this.clock = clock;
    }

    @Override
    public WeatherResponseDto getForecast(Double lat, Double lon, LocalDate startDate, LocalDate endDate) {
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180
                || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return fetchForecast(lat, lon, startDate, endDate);
        }

        ForecastKey key = new ForecastKey(providerCoordinate(lat), providerCoordinate(lon), startDate, endDate);
        long startedAt = System.nanoTime();
        WeatherResponseDto cached = getCached(key);
        if (cached != null) {
            log.info("External request completed: service=weather stage=forecast outcome=cache_hit durationMs={}",
                    elapsedMillis(startedAt));
            return cached;
        }

        CompletableFuture<WeatherResponseDto> pending = new CompletableFuture<>();
        CompletableFuture<WeatherResponseDto> existing = inFlight.putIfAbsent(key, pending);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException exception) {
                return apiFailure();
            }
        }

        try {
            WeatherResponseDto response = fetchForecast(lat, lon, startDate, endDate);
            if (response.getStatus() != WeatherResponseDto.Status.API_ERROR) {
                putCached(key, response);
            }
            pending.complete(response);
            log.info("External request completed: service=weather stage=forecast outcome={} durationMs={}",
                    response.getStatus(), elapsedMillis(startedAt));
            return response;
        } catch (RuntimeException exception) {
            pending.complete(apiFailure());
            log.warn("External request completed: service=weather stage=forecast outcome=exception durationMs={}",
                    elapsedMillis(startedAt));
            return apiFailure();
        } finally {
            inFlight.remove(key, pending);
        }
    }

    private WeatherResponseDto fetchForecast(Double lat, Double lon, LocalDate startDate, LocalDate endDate) {
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return WeatherResponseDto.builder()
                    .status(WeatherResponseDto.Status.API_ERROR)
                    .message("Invalid coordinates provided for weather forecast.")
                    .build();
        }

        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return WeatherResponseDto.builder()
                    .status(WeatherResponseDto.Status.API_ERROR)
                    .message("Invalid travel dates provided.")
                    .build();
        }

        try {
            // Using Open-Meteo Forecast API (supports up to 16 days daily forecast)
            String url = String.format("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max&forecast_days=16&timezone=auto", lat, lon);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("daily")) {
                // If trip dates are far in the future or daily is missing
                LocalDate today = LocalDate.now();
                if (startDate.isAfter(today.plusDays(16))) {
                    return WeatherResponseDto.builder()
                            .status(WeatherResponseDto.Status.NOT_AVAILABLE_YET)
                            .message("Detailed weather forecasts are not available yet for your selected travel dates. Weather forecasts will become available closer to your trip.")
                            .build();
                }
                return WeatherResponseDto.builder()
                        .status(WeatherResponseDto.Status.API_ERROR)
                        .message("Weather service is currently unreachable.")
                        .build();
            }

            Map<String, Object> daily = (Map<String, Object>) response.get("daily");
            List<String> times = (List<String>) daily.get("time");
            List<Number> maxTemps = (List<Number>) daily.get("temperature_2m_max");
            List<Number> minTemps = (List<Number>) daily.get("temperature_2m_min");
            List<Number> weatherCodes = (List<Number>) daily.get("weather_code");
            if (weatherCodes == null) {
                weatherCodes = (List<Number>) daily.get("weathercode"); // Fallback
            }
            List<Number> precipProbs = (List<Number>) daily.get("precipitation_probability_max");

            Map<LocalDate, Map<String, Object>> dateToData = new HashMap<>();
            if (times != null) {
                for (int i = 0; i < times.size(); i++) {
                    LocalDate date = LocalDate.parse(times.get(i));
                    Map<String, Object> dayMap = new HashMap<>();
                    dayMap.put("max", maxTemps != null && maxTemps.get(i) != null ? maxTemps.get(i).doubleValue() : 0.0);
                    dayMap.put("min", minTemps != null && minTemps.get(i) != null ? minTemps.get(i).doubleValue() : 0.0);
                    int code = weatherCodes != null && weatherCodes.get(i) != null ? weatherCodes.get(i).intValue() : -1;
                    dayMap.put("code", code);
                    double precip = precipProbs != null && precipProbs.get(i) != null ? precipProbs.get(i).doubleValue() : 0.0;
                    dayMap.put("precip", precip);
                    dateToData.put(date, dayMap);
                }
            }

            List<WeatherForecastDto> forecasts = new ArrayList<>();
            List<LocalDate> missingDates = new ArrayList<>();

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                if (!dateToData.containsKey(date)) {
                    missingDates.add(date);
                    continue;
                }

                Map<String, Object> dayData = dateToData.get(date);
                double maxTemp = (Double) dayData.get("max");
                double minTemp = (Double) dayData.get("min");
                int code = (Integer) dayData.get("code");
                double precip = (Double) dayData.get("precip");

                forecasts.add(WeatherForecastDto.builder()
                        .date(date.toString())
                        .condition(mapWeatherCodeToCondition(code))
                        .icon(mapWeatherCodeToIcon(code))
                        .minTemperature(minTemp)
                        .maxTemperature(maxTemp)
                        .rain(precip)
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
            log.warn("Weather provider failed. exceptionType={}", e.getClass().getSimpleName());
            return WeatherResponseDto.builder()
                    .status(WeatherResponseDto.Status.API_ERROR)
                    .message("Weather information could not be loaded at the moment.")
                    .build();
        }
    }

    private synchronized WeatherResponseDto getCached(ForecastKey key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAtMillis() <= clock.millis()) {
            cache.remove(key);
            return null;
        }
        return entry.response();
    }

    private synchronized void putCached(ForecastKey key, WeatherResponseDto response) {
        cache.put(key, new CacheEntry(response, clock.millis() + CACHE_TTL_MILLIS));
        while (cache.size() > MAX_CACHE_ENTRIES) {
            ForecastKey eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    private String providerCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.4f", coordinate);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private WeatherResponseDto apiFailure() {
        return WeatherResponseDto.builder()
                .status(WeatherResponseDto.Status.API_ERROR)
                .message("Weather information could not be loaded at the moment.")
                .build();
    }

    private record ForecastKey(String latitude, String longitude, LocalDate startDate, LocalDate endDate) {}
    private record CacheEntry(WeatherResponseDto response, long expiresAtMillis) {}

    private String mapWeatherCodeToCondition(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Foggy";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing Drizzle";
            case 61:
            case 63:
            case 65: return "Rain";
            case 66:
            case 67: return "Freezing Rain";
            case 71:
            case 73:
            case 75:
            case 77: return "Snow";
            case 80:
            case 81:
            case 82: return "Rain Showers";
            case 85:
            case 86: return "Snow Showers";
            case 95:
            case 96:
            case 99: return "Thunderstorm";
            default: return "Unknown";
        }
    }

    private String mapWeatherCodeToIcon(int code) {
        switch (code) {
            case 0: return "01d";
            case 1: return "02d";
            case 2: return "03d";
            case 3: return "04d";
            case 45:
            case 48: return "50d";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
            case 80:
            case 81:
            case 82: return "09d";
            case 61:
            case 63:
            case 65: return "10d";
            case 66:
            case 67:
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86: return "13d";
            case 95:
            case 96:
            case 99: return "11d";
            default: return "";
        }
    }
}
