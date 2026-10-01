package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.util.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NominatimGeocodingService implements GeocodingService {

    private final RestTemplate restTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    private record CacheEntry(GeoLocation value, long timestamp) {}

    @Override
    public GeoLocation geocodeCity(String city) {
        if (city == null) {
            return null;
        }

        // Normalize input: trim leading/trailing whitespace, collapse repeated whitespace
        String normalized = city.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        CacheEntry entry = cache.get(normalized);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            log.info("Geocoding cache hit for input='{}' normalized='{}'", city, normalized);
            return entry.value();
        }

        try {
            long startedAt = System.nanoTime();
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", normalized)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 10)
                    .queryParam("addressdetails", 1)
                    .queryParam("countrycodes", "in")
                    .encode()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "TripPlannerApp-1.0-UniqueContact-dilip@example.com");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> candidates = response.getBody();

            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> bestCandidate = selectBestCandidate(candidates, normalized);

                if (bestCandidate != null) {
                    double lat = Double.parseDouble(String.valueOf(bestCandidate.get("lat")));
                    double lon = Double.parseDouble(String.valueOf(bestCandidate.get("lon")));
                    String displayName = String.valueOf(bestCandidate.get("display_name"));
                    String type = String.valueOf(bestCandidate.get("type"));
                    String cls = String.valueOf(bestCandidate.get("class"));
                    Object importanceObj = bestCandidate.get("importance");
                    double importance = importanceObj != null ? Double.parseDouble(String.valueOf(importanceObj)) : 0.0;
                    
                    Map<String, Object> address = (Map<String, Object>) bestCandidate.get("address");
                    String country = address != null ? (String) address.get("country") : null;
                    String state = address != null ? (String) address.get("state") : null;

                    GeoLocation loc = new GeoLocation(lat, lon);
                    cache.put(normalized, new CacheEntry(loc, System.currentTimeMillis()));

                    log.info("Nominatim geocoded input='{}' (normalized='{}') -> selected='{}' (lat={}, lon={}, type={}, class={}, state={}, country={}, importance={}) in {} ms",
                            city, normalized, displayName, lat, lon, type, cls, state, country, importance, (System.nanoTime() - startedAt) / 1_000_000);
                    return loc;
                }
            }
        } catch (Exception e) {
            log.error("Geocoding failed for city='{}': {}", city, e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> selectBestCandidate(List<Map<String, Object>> candidates, String queryCity) {
        Map<String, Object> best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Map<String, Object> candidate : candidates) {
            String type = String.valueOf(candidate.get("type"));
            String cls = String.valueOf(candidate.get("class"));
            String displayName = String.valueOf(candidate.get("display_name"));
            String lowerDisplay = displayName.toLowerCase();
            Object importanceObj = candidate.get("importance");
            double importance = importanceObj != null ? Double.parseDouble(String.valueOf(importanceObj)) : 0.0;
            Map<String, Object> address = (Map<String, Object>) candidate.get("address");

            String country = address != null ? (String) address.get("country") : null;
            String countryCode = address != null ? (String) address.get("country_code") : null;
            String state = address != null ? (String) address.get("state") : null;

            double score = 0.0;

            // 1. India preference (India-focused trip planner)
            if ((countryCode != null && countryCode.equalsIgnoreCase("in")) ||
                (country != null && country.toLowerCase().contains("india")) ||
                lowerDisplay.contains("india")) {
                score += 5000.0;
            } else {
                score -= 10000.0;
            }

            // 2. Prioritize major city, administrative, town, municipality entities
            if ("city".equalsIgnoreCase(type) || ("place".equalsIgnoreCase(cls) && "city".equalsIgnoreCase(type))) {
                score += 1500.0;
            } else if ("administrative".equalsIgnoreCase(type) || "administrative".equalsIgnoreCase(cls)) {
                score += 1000.0;
            } else if ("town".equalsIgnoreCase(type) || "municipality".equalsIgnoreCase(type)) {
                score += 800.0;
            } else if ("village".equalsIgnoreCase(type)) {
                score += 200.0;
            }

            // 3. Heavy penalty for suburbs, neighbourhoods, localities, villages, hamlets, residential areas
            if (isSuburbanOrMinor(type)) {
                score -= 2000.0;
            }

            // 4. Importance score factor
            score += importance * 100.0;

            // 5. Name match bonus
            String lowerQuery = queryCity.toLowerCase();
            if (lowerDisplay.contains(lowerQuery)) {
                score += 200.0;
            }
            if (address != null) {
                String cityNameInAddr = (String) address.get("city");
                if (cityNameInAddr == null) cityNameInAddr = (String) address.get("town");
                if (cityNameInAddr == null) cityNameInAddr = (String) address.get("municipality");
                if (cityNameInAddr == null) cityNameInAddr = (String) address.get("state_district");
                if (cityNameInAddr != null && cityNameInAddr.toLowerCase().contains(lowerQuery)) {
                    score += 300.0;
                }
            }

            log.debug("Candidate: displayName={}, type={}, cls={}, state={}, country={}, importance={}, score={}", displayName, type, cls, state, country, importance, score);

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best != null ? best : candidates.get(0);
    }

    private boolean isSuburbanOrMinor(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.equals("suburb") ||
               t.equals("neighbourhood") ||
               t.equals("locality") ||
               t.equals("residential") ||
               t.equals("village") ||
               t.equals("hamlet") ||
               t.equals("isolated_dwelling") ||
               t.equals("quarter") ||
               t.equals("borough") ||
               t.equals("city_block") ||
               t.equals("allotments") ||
               t.equals("plot");
    }
}
