package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.util.PlaceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OverpassServiceImpl implements OverpassService {

    private final RestTemplate restTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    private record CacheEntry(List<PlaceResult> value, long timestamp) {}

    private static final List<String> OVERPASS_URLS = List.of(
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
    );

    @Override
    public List<PlaceResult> getTouristPlaces(double lat, double lon) {
        String key = "places_" + lat + "_" + lon;
        CacheEntry entry = cache.get(key);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            return entry.value();
        }

        // Expanded query to increase chances of finding results without timing out.
        // Removed less critical categories that might bloat the result set.
        String query = """
        [out:json][timeout:60];
        (
          nwr["tourism"~"attraction|museum|viewpoint|zoo|theme_park"]["name"](around:5000,%f,%f);
          nwr["historic"~"monument|castle|ruins|fort"]["name"](around:5000,%f,%f);
          nwr["leisure"~"park|garden"]["name"](around:5000,%f,%f);
        );
        out center;
        """.formatted(lat, lon, lat, lon, lat, lon);

        List<PlaceResult> results = fetch("tourist places", query);
        cacheResult(key, results);
        return results;
    }

    @Override
    public List<PlaceResult> getHotels(double lat, double lon) {
        String key = "hotels_" + lat + "_" + lon;
        CacheEntry entry = cache.get(key);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            return entry.value();
        }

        String query = """
        [out:json][timeout:25];
        (
          nwr['tourism'~'hotel|guest_house|hostel|motel|apartment']['name'](around:2000,%f,%f);
        );
        out center;
        """.formatted(lat, lon);

        List<PlaceResult> results = fetch("hotels", query);
        cacheResult(key, results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private void cacheResult(String key, List<PlaceResult> results) {
        // Do not turn a transient Overpass failure into a permanently empty city.
        if (!results.isEmpty()) {
            cache.put(key, new CacheEntry(results, System.currentTimeMillis()));
        }
    }

    private List<PlaceResult> fetch(String dataType, String query) {

        for (String overpassUrl : OVERPASS_URLS) {
            try {
            long startedAt = System.nanoTime();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> entity =
                    new HttpEntity<>("data=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), headers);

            Map<String, Object> response =
                    restTemplate.postForObject(overpassUrl, entity, Map.class);

            if (response == null || !response.containsKey("elements")) {
                continue;
            }

            List<Map<String, Object>> elements =
                    (List<Map<String, Object>>) response.get("elements");
            log.info("Overpass {} returned {} elements", dataType, elements.size());

            List<PlaceResult> results = new ArrayList<>();

            for (Map<String, Object> el : elements) {

                Map<String, Object> tags =
                        (Map<String, Object>) el.get("tags");

                if (tags == null || !tags.containsKey("name")) continue;
                
                String name = (String) tags.get("name");
                if ("hotels".equals(dataType) && isInvalidHotel(name, tags)) continue;

                Double latVal = (Double) el.get("lat");
                Double lonVal = (Double) el.get("lon");

                if ((latVal == null || lonVal == null) && el.containsKey("center")) {
                    Map<String, Object> center =
                            (Map<String, Object>) el.get("center");
                    latVal = (Double) center.get("lat");
                    lonVal = (Double) center.get("lon");
                }

                if (latVal == null || lonVal == null) continue;

                // ✅ Wikimedia image support
                String imageUrl = null;
                if (tags.containsKey("wikimedia_commons")) {
                    imageUrl =
                            "https://commons.wikimedia.org/wiki/Special:FilePath/"
                                    + tags.get("wikimedia_commons");
                }
                
                java.util.Map<String, String> stringTags = new java.util.HashMap<>();
                for (Map.Entry<String, Object> entry : tags.entrySet()) {
                    stringTags.put(entry.getKey(), String.valueOf(entry.getValue()));
                }

                results.add(
                        PlaceResult.builder()
                                .name((String) tags.get("name"))
                                .address(addressFrom(tags))
                                .latitude(latVal)
                                .longitude(lonVal)
                                .imageUrl(imageUrl)
                                .tags(stringTags)
                                .build()
                );
            }

            log.info("Overpass {} returned {} results from {} in {} ms", dataType, results.size(), overpassUrl,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return results;

            } catch (Exception e) {
                log.warn("Overpass {} query failed at {}: {}", dataType, overpassUrl, e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    private boolean isInvalidHotel(String name, Map<String, Object> tags) {
        String lowerName = name.toLowerCase();

        // Keywords to exclude
        List<String> keywords = List.of(
            "boys", "girls", "student", "pg", "residential", "shop", "restaurant", "cafe"
        );
        
        for (String keyword : keywords) {
            if (lowerName.contains(keyword)) return true;
        }

        // Tag-based check
        if (tags != null) {
            if (tags.containsKey("shop") || "restaurant".equals(tags.get("amenity")) || "cafe".equals(tags.get("amenity"))) {
                return true;
            }
        }

        return false;
    }

    private String addressFrom(Map<String, Object> tags) {
        Object fullAddress = tags.get("addr:full");
        if (fullAddress != null) return String.valueOf(fullAddress);

        return java.util.stream.Stream.of("addr:housenumber", "addr:street", "addr:city")
                .map(tags::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
