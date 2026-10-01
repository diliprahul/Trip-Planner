package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.util.PlaceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OverpassServiceImpl implements OverpassService {

    private final RestTemplate overpassRestTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    private record CacheEntry(List<PlaceResult> value, long timestamp) {}
    private record FetchResult(List<PlaceResult> results, boolean timedOut) {}

    private static final List<String> OVERPASS_URLS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter"
    );

    public OverpassServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.overpassRestTemplate = new RestTemplate(factory);
        this.overpassRestTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("User-Agent", "TripPlanner/1.0 (Overpass Client)");
            return execution.execute(request, body);
        });
    }

    @Override
    public List<PlaceResult> getTouristPlaces(double lat, double lon) {
        return getTouristPlaces(lat, lon, 7);
    }

    @Override
    public List<PlaceResult> getTouristPlaces(double lat, double lon, int minCount) {
        String key = "places_" + lat + "_" + lon + "_" + minCount;
        CacheEntry entry = cache.get(key);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            return entry.value();
        }

        // Attempt 1: 15km radius
        String query1 = """
        [out:json][timeout:15];
        (
          nw["tourism"~"attraction|museum|viewpoint|zoo|theme_park"]["name"](around:15000,%f,%f);
          nw["historic"~"monument|castle|ruins|fort"]["name"](around:15000,%f,%f);
          nw["leisure"~"park|garden"]["name"](around:15000,%f,%f);
        );
        out center qt;
        """.formatted(lat, lon, lat, lon, lat, lon);

        FetchResult attempt1 = fetch("Places Overpass", query1);
        List<PlaceResult> results = attempt1.results();

        // Fallback attempt 2 only if attempt 1 did NOT time out/fail and results are fewer than minCount
        if (!attempt1.timedOut() && results.size() < minCount) {
            log.info("Initial tourist places count ({}) is less than requested minCount ({}), running fallback wider query (20km)", results.size(), minCount);
            String query2 = """
            [out:json][timeout:15];
            (
              nw["tourism"~"attraction|museum|viewpoint|zoo|theme_park"]["name"](around:20000,%f,%f);
              nw["historic"~"monument|castle|ruins|fort"]["name"](around:20000,%f,%f);
              nw["leisure"~"park|garden"]["name"](around:20000,%f,%f);
            );
            out center qt;
            """.formatted(lat, lon, lat, lon, lat, lon);
            FetchResult attempt2 = fetch("Places Overpass fallback", query2);
            List<PlaceResult> fallbackResults = attempt2.results();

            Map<String, PlaceResult> uniqueMap = new LinkedHashMap<>();
            for (PlaceResult p : results) {
                if (p.getName() != null) uniqueMap.put(p.getName().toLowerCase(Locale.ROOT).trim(), p);
            }
            for (PlaceResult p : fallbackResults) {
                if (p.getName() != null) uniqueMap.putIfAbsent(p.getName().toLowerCase(Locale.ROOT).trim(), p);
            }
            results = new ArrayList<>(uniqueMap.values());
        } else if (attempt1.timedOut()) {
            log.warn("Skipping tourist places fallback due to Overpass timeout/failure on primary attempt.");
        }

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

        // Attempt 1: 5km radius
        String query1 = """
        [out:json][timeout:15];
        (
          nw['tourism'~'hotel|guest_house|hostel|motel|apartment|resort|inn|lodge']['name'](around:5000,%f,%f);
        );
        out center qt;
        """.formatted(lat, lon);

        FetchResult attempt1 = fetch("Hotels Overpass", query1);
        List<PlaceResult> results = attempt1.results();

        long validCount = results.stream().filter(h -> h.getName() != null && !isInvalidHotel(h.getName(), h.getTags())).count();

        // Fallback attempt 2 only if attempt 1 did NOT time out/fail and 0 valid hotels found
        if (!attempt1.timedOut() && validCount == 0) {
            log.info("Initial hotel search around 5km returned 0 valid hotels, running fallback wider query (10km)");
            String query2 = """
            [out:json][timeout:15];
            (
              nw['tourism'~'hotel|guest_house|hostel|motel|apartment|resort|inn|lodge']['name'](around:10000,%f,%f);
            );
            out center qt;
            """.formatted(lat, lon);
            FetchResult attempt2 = fetch("Hotels Overpass fallback", query2);
            List<PlaceResult> fallbackResults = attempt2.results();
            
            Map<String, PlaceResult> uniqueMap = new LinkedHashMap<>();
            for (PlaceResult h : results) {
                if (h.getName() != null) uniqueMap.put(h.getName().toLowerCase(Locale.ROOT).trim(), h);
            }
            for (PlaceResult h : fallbackResults) {
                if (h.getName() != null) uniqueMap.putIfAbsent(h.getName().toLowerCase(Locale.ROOT).trim(), h);
            }
            results = new ArrayList<>(uniqueMap.values());
        } else if (attempt1.timedOut()) {
            log.warn("Skipping hotels fallback due to Overpass timeout/failure on primary attempt.");
        }

        cacheResult(key, results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private void cacheResult(String key, List<PlaceResult> results) {
        if (!results.isEmpty()) {
            cache.put(key, new CacheEntry(results, System.currentTimeMillis()));
        }
    }

    @SuppressWarnings("unchecked")
    private FetchResult fetch(String dataType, String query) {
        boolean hadTimeoutOrNetworkError = false;
        String logPrefix = dataType.toLowerCase().contains("hotel") ? "Overpass Hotels" : "Overpass Places";

        for (String overpassUrl : OVERPASS_URLS) {
            long startedAt = System.nanoTime();
            log.info("{} request started", logPrefix);
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                HttpEntity<String> entity =
                        new HttpEntity<>("data=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), headers);

                Map<String, Object> response =
                        overpassRestTemplate.postForObject(overpassUrl, entity, Map.class);

                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                log.info("{} request completed in {} ms", logPrefix, durationMs);

                if (response == null || !response.containsKey("elements")) {
                    return new FetchResult(Collections.emptyList(), false);
                }

                List<Map<String, Object>> elements =
                        (List<Map<String, Object>>) response.get("elements");
                
                List<PlaceResult> results = new ArrayList<>();

                for (Map<String, Object> el : elements) {

                    Map<String, Object> tags =
                            (Map<String, Object>) el.get("tags");

                    if (tags == null || !tags.containsKey("name")) continue;
                    
                    Map<String, String> stringTags = new java.util.HashMap<>();
                    for (Map.Entry<String, Object> entry : tags.entrySet()) {
                        stringTags.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }

                    String name = (String) tags.get("name");
                    if (logPrefix.contains("Hotels") && isInvalidHotel(name, stringTags)) continue;

                    Double latVal = (Double) el.get("lat");
                    Double lonVal = (Double) el.get("lon");

                    if ((latVal == null || lonVal == null) && el.containsKey("center")) {
                        Map<String, Object> center =
                                (Map<String, Object>) el.get("center");
                        latVal = (Double) center.get("lat");
                        lonVal = (Double) center.get("lon");
                    }

                    if (latVal == null || lonVal == null) continue;

                    String imageUrl = null;
                    if (tags.containsKey("wikimedia_commons")) {
                        imageUrl =
                                "https://commons.wikimedia.org/wiki/Special:FilePath/"
                                        + tags.get("wikimedia_commons");
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

                return new FetchResult(results, false);

            } catch (Exception e) {
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                if (isTimeoutOrNetworkError(e)) {
                    hadTimeoutOrNetworkError = true;
                    log.warn("{} request timed out after {} ms", logPrefix, durationMs);
                } else {
                    log.warn("{} request failed after {} ms: {}", logPrefix, durationMs, e.getMessage());
                    hadTimeoutOrNetworkError = true;
                }
            }
        }

        return new FetchResult(Collections.emptyList(), hadTimeoutOrNetworkError);
    }

    private boolean isTimeoutOrNetworkError(Exception e) {
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException 
                || cause instanceof java.net.ConnectException 
                || cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isInvalidHotel(String name, Map<String, String> tags) {
        String lowerName = name.toLowerCase();

        List<String> keywords = List.of(
            "boys", "girls", "student", "pg", "residential", "shop", "restaurant", "cafe"
        );
        
        for (String keyword : keywords) {
            if (lowerName.contains(keyword)) return true;
        }

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
