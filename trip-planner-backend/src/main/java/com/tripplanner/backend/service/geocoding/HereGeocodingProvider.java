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
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class HereGeocodingProvider implements GeocodingProvider {
    private static final String GEOCODE_URL = "https://geocode.search.hereapi.com/v1/geocode";
    private final RestTemplate restTemplate;

    @Value("${geocoding.here.enabled:true}")
    private boolean enabled;

    @Value("${here.api-key:}")
    private String apiKey;

    @Override
    public String getProviderName() {
        return "HERE";
    }

    @Override
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank() && !apiKey.equals("${HERE_API_KEY}");
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
                .queryParam("q", query)
                .queryParam("apiKey", apiKey)
                .encode()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty()) {
                return null;
            }

            for (Map<String, Object> item : items) {
                String title = String.valueOf(item.getOrDefault("title", "")).toLowerCase(Locale.ROOT);
                Map<String, Object> address = (Map<String, Object>) item.get("address");
                String label = address != null ? String.valueOf(address.getOrDefault("label", "")).toLowerCase(Locale.ROOT) : "";

                if (requireNamedPlace) {
                    if (destination != null && !destination.isBlank() && !title.contains(normalize(destination)) && !label.contains(normalize(destination))) {
                        continue;
                    }
                    if (expectedName != null && !expectedName.isBlank()) {
                        if (!matchesName(title + " " + label, expectedName)) {
                            continue;
                        }
                    }
                }

                Map<String, Object> position = (Map<String, Object>) item.get("position");
                if (position == null) continue;

                Double lat = number(position.get("lat"));
                Double lon = number(position.get("lng"));

                if (valid(lat, lon)) {
                    log.info("HERE Geocoding resolved query successfully");
                    return new GeoLocation(lat, lon);
                }
            }
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
            log.warn("HERE Geocoding HTTP error {}", statusCode);
            if (statusCode == 429 || statusCode >= 500) {
                throw e;
            }
        } catch (ResourceAccessException e) {
            log.warn("HERE Geocoding network/timeout error: {}", e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.warn("HERE Geocoding exception: {}", e.getClass().getSimpleName());
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
