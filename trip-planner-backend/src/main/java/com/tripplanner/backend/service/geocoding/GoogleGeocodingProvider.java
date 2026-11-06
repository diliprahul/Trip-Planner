package com.tripplanner.backend.service.geocoding;

import com.tripplanner.backend.util.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class GoogleGeocodingProvider implements GeocodingProvider {
    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private final RestTemplate restTemplate;

    @Value("${geocoding.google.enabled:true}")
    private boolean enabled;

    @Value("${google.maps.api-key:}")
    private String apiKey;

    @Override
    public String getProviderName() {
        return "Google";
    }

    @Override
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank() && !apiKey.equals("${GOOGLE_MAPS_API_KEY}");
    }

    @Override
    public GeoLocation geocode(String query, String expectedName, String destination, boolean requireNamedPlace) {
        if (!isEnabled()) {
            return null;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(GEOCODE_URL)
                .queryParam("address", query)
                .queryParam("key", apiKey)
                .encode()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }

            String status = String.valueOf(body.get("status"));
            if ("OVER_QUERY_LIMIT".equalsIgnoreCase(status) || "REQUEST_DENIED".equalsIgnoreCase(status)) {
                log.warn("Google Geocoding API rate limited or denied status='{}'", status);
                throw new HttpStatusCodeException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, status) {};
            }
            if (!"OK".equalsIgnoreCase(status)) {
                log.warn("Google Geocoding API returned status='{}' for query", status);
                return null;
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
            if (results == null || results.isEmpty()) {
                return null;
            }

            for (Map<String, Object> result : results) {
                String formattedAddress = String.valueOf(result.getOrDefault("formatted_address", "")).toLowerCase(Locale.ROOT);
                if (requireNamedPlace) {
                    if (destination != null && !destination.isBlank() && !formattedAddress.contains(normalize(destination))) {
                        continue;
                    }
                    if (expectedName != null && !expectedName.isBlank()) {
                        if (!matchesName(formattedAddress, expectedName)) {
                            continue;
                        }
                    }
                }

                Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
                if (geometry == null) continue;
                Map<String, Object> location = (Map<String, Object>) geometry.get("location");
                if (location == null) continue;

                Double lat = number(location.get("lat"));
                Double lon = number(location.get("lng"));

                if (valid(lat, lon)) {
                    log.info("Google Geocoding resolved query successfully");
                    return new GeoLocation(lat, lon);
                }
            }
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
            log.warn("Google Geocoding HTTP error {}", statusCode);
            if (statusCode == 429 || statusCode >= 500) {
                throw e;
            }
        } catch (ResourceAccessException e) {
            log.warn("Google Geocoding network/timeout error: {}", e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.warn("Google Geocoding exception: {}", e.getClass().getSimpleName());
            if (e instanceof RuntimeException re) throw re;
        }

        return null;
    }

    private boolean matchesName(String display, String name) {
        if (name == null || name.isBlank()) return true;
        List<String> significantTokens = Arrays.stream(normalize(name).split(" ")).filter(t -> t.length() > 2).toList();
        if (significantTokens.isEmpty()) return true;
        List<String> displayTokens = Arrays.stream(normalize(display).split(" ")).toList();
        long matchingTokens = significantTokens.stream()
                .filter(token -> displayTokens.stream().anyMatch(displayToken -> displayToken.contains(token) || token.contains(displayToken)))
                .count();
        return matchingTokens > 0;
    }

    private Double number(Object value) {
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean valid(Double lat, Double lon) {
        return lat != null && lon != null
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180
                && !(lat == 0.0 && lon == 0.0);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim().replaceAll("\\s+", " ");
    }
}
