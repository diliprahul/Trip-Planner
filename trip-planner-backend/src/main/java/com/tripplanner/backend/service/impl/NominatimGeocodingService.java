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
        CacheEntry entry = cache.get(city);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            return entry.value();
        }

        try {
            long startedAt = System.nanoTime();
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", city)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .queryParam("addressdetails", 0)
                    .encode()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "TripPlannerApp-1.0-UniqueContact-dilip@example.com");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, String>> res = response.getBody();

            if (res != null && !res.isEmpty()) {
                GeoLocation loc = new GeoLocation(
                        Double.parseDouble(res.get(0).get("lat")),
                        Double.parseDouble(res.get(0).get("lon"))
                );
                cache.put(city, new CacheEntry(loc, System.currentTimeMillis()));
                log.info("Nominatim geocoded {} in {} ms", city, (System.nanoTime() - startedAt) / 1_000_000);
                return loc;
            }
        } catch (Exception e) {
            log.error("Geocoding failed for {}: {}", city, e.getMessage());
        }

        return null;
    }
}
