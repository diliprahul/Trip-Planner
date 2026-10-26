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

    private static final long PLACES_TIME_BUDGET_MS = 15_000; // 15 seconds strict time budget for places
    private static final long HOTELS_TIME_BUDGET_MS = 10_000; // 10 seconds strict time budget for hotels

    public OverpassServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(8_000);
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
        String key = "places_" + lat + "_" + lon;
        CacheEntry entry = cache.get(key);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS)) {
            return entry.value();
        }

        long startTime = System.currentTimeMillis();
        long deadline = startTime + PLACES_TIME_BUDGET_MS;

        // Attempt 1: 15km radius with timeout 8s
        String query1 = """
        [out:json][timeout:8];
        (
          nw["tourism"~"attraction|museum|viewpoint|zoo|theme_park|artwork|gallery"]["name"](around:15000,%f,%f);
          nw["historic"~"monument|castle|ruins|fort|palace|tomb|archaeological_site|heritage"]["name"](around:15000,%f,%f);
          nw["leisure"~"park|garden|nature_reserve"]["name"](around:15000,%f,%f);
          nw["amenity"~"place_of_worship|marketplace|arts_centre|theatre"]["name"](around:15000,%f,%f);
          nw["shop"~"mall|department_store"]["name"](around:15000,%f,%f);
          nw["natural"~"peak|water|beach|cave_entrance|hot_spring"]["name"](around:15000,%f,%f);
        );
        out center qt;
        """.formatted(lat, lon, lat, lon, lat, lon, lat, lon, lat, lon, lat, lon);

        FetchResult attempt1 = fetch("Places Overpass", query1, deadline);
        List<PlaceResult> results = attempt1.results();

        // Fallback attempt 2 if results are fewer than minCount and time budget remains
        if (results.size() < minCount && System.currentTimeMillis() < deadline) {
            long remainingBudget = deadline - System.currentTimeMillis();
            if (remainingBudget > 3000) {
                log.info("Initial tourist places count ({}) is less than requested minCount ({}), running fallback wider query (20km)", results.size(), minCount);
                String query2 = """
                [out:json][timeout:6];
                (
                  nw["tourism"~"attraction|museum|viewpoint|zoo|theme_park|artwork|gallery"]["name"](around:20000,%f,%f);
                  nw["historic"~"monument|castle|ruins|fort|palace|tomb|archaeological_site|heritage"]["name"](around:20000,%f,%f);
                  nw["leisure"~"park|garden|nature_reserve"]["name"](around:20000,%f,%f);
                  nw["amenity"~"place_of_worship|marketplace|arts_centre|theatre"]["name"](around:20000,%f,%f);
                  nw["shop"~"mall|department_store"]["name"](around:20000,%f,%f);
                  nw["natural"~"peak|water|beach|cave_entrance|hot_spring"]["name"](around:20000,%f,%f);
                );
                out center qt;
                """.formatted(lat, lon, lat, lon, lat, lon, lat, lon, lat, lon, lat, lon);
                FetchResult attempt2 = fetch("Places Overpass fallback", query2, deadline);
                List<PlaceResult> fallbackResults = attempt2.results();

                Map<String, PlaceResult> uniqueMap = new LinkedHashMap<>();
                for (PlaceResult p : results) {
                    if (p.getName() != null) uniqueMap.put(p.getName().toLowerCase(Locale.ROOT).trim(), p);
                }
                for (PlaceResult p : fallbackResults) {
                    if (p.getName() != null) uniqueMap.putIfAbsent(p.getName().toLowerCase(Locale.ROOT).trim(), p);
                }
                results = new ArrayList<>(uniqueMap.values());
            } else {
                log.warn("Skipping places fallback query due to near-exhausted time budget ({} ms remaining)", remainingBudget);
            }
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

        long startTime = System.currentTimeMillis();
        long deadline = startTime + HOTELS_TIME_BUDGET_MS;

        // Attempt 1: 5km radius with broader accommodation queries
        String query1 = """
        [out:json][timeout:6];
        (
          nw["tourism"~"hotel|guest_house|hostel|motel|apartment|resort|inn|lodge|chalet|alpine_hut|wilderness_hut"](around:5000,%f,%f);
          nw["amenity"="hotel"](around:5000,%f,%f);
          nw["building"="hotel"](around:5000,%f,%f);
        );
        out center qt;
        """.formatted(lat, lon, lat, lon, lat, lon);

        FetchResult attempt1 = fetch("Hotels Overpass", query1, deadline);
        List<PlaceResult> results = attempt1.results();

        long validCount = results.stream().filter(h -> h.getName() != null && !isInvalidHotel(h.getName(), h.getTags())).count();
        log.info("Initial hotel search around 5km returned {} raw results, {} valid hotels", results.size(), validCount);

        // Fallback attempt 2 if 0 valid hotels found and time budget remains
        if (validCount == 0 && System.currentTimeMillis() < deadline) {
            long remainingBudget = deadline - System.currentTimeMillis();
            if (remainingBudget > 2000) {
                log.info("Initial hotel search around 5km returned 0 valid hotels, running fallback wider query (10km)");
                String query2 = """
                [out:json][timeout:5];
                (
                  nw["tourism"~"hotel|guest_house|hostel|motel|apartment|resort|inn|lodge|chalet|alpine_hut|wilderness_hut"](around:10000,%f,%f);
                  nw["amenity"="hotel"](around:10000,%f,%f);
                  nw["building"="hotel"](around:10000,%f,%f);
                );
                out center qt;
                """.formatted(lat, lon, lat, lon, lat, lon);
                FetchResult attempt2 = fetch("Hotels Overpass fallback", query2, deadline);
                List<PlaceResult> fallbackResults = attempt2.results();
                long fallbackValidCount = fallbackResults.stream().filter(h -> h.getName() != null && !isInvalidHotel(h.getName(), h.getTags())).count();
                log.info("Fallback hotel search around 10km returned {} raw results, {} valid hotels", fallbackResults.size(), fallbackValidCount);

                Map<String, PlaceResult> uniqueMap = new LinkedHashMap<>();
                for (PlaceResult h : results) {
                    if (h.getName() != null) uniqueMap.put(h.getName().toLowerCase(Locale.ROOT).trim(), h);
                }
                for (PlaceResult h : fallbackResults) {
                    if (h.getName() != null) uniqueMap.putIfAbsent(h.getName().toLowerCase(Locale.ROOT).trim(), h);
                }
                results = new ArrayList<>(uniqueMap.values());
            } else {
                log.warn("Skipping hotel fallback query due to near-exhausted time budget");
            }
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
    private FetchResult fetch(String dataType, String query, long deadline) {
        boolean hadTimeoutOrNetworkError = false;
        String logPrefix = dataType.toLowerCase().contains("hotel") ? "Overpass Hotels" : "Overpass Places";
        int maxRetriesPerUrl = 1; // Bounded retries

        for (String overpassUrl : OVERPASS_URLS) {
            if (System.currentTimeMillis() >= deadline) {
                log.warn("{} fetch aborted: overall time budget exhausted before trying {}", logPrefix, overpassUrl);
                break;
            }

            for (int retry = 0; retry <= maxRetriesPerUrl; retry++) {
                if (System.currentTimeMillis() >= deadline) {
                    log.warn("{} fetch aborted during retries: time budget exhausted", logPrefix);
                    break;
                }

                long startedAt = System.nanoTime();
                log.info("{} request started on {} (attempt {})", logPrefix, overpassUrl, retry + 1);
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                    HttpEntity<String> entity =
                            new HttpEntity<>("data=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), headers);

                    Map<String, Object> response =
                            overpassRestTemplate.postForObject(overpassUrl, entity, Map.class);

                    long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                    log.info("{} request completed on {} in {} ms", logPrefix, overpassUrl, durationMs);

                    if (response == null || !response.containsKey("elements")) {
                        return new FetchResult(Collections.emptyList(), false);
                    }

                    List<Map<String, Object>> elements =
                            (List<Map<String, Object>>) response.get("elements");

                    List<PlaceResult> results = new ArrayList<>();

                    for (Map<String, Object> el : elements) {
                        Map<String, Object> tags =
                                (Map<String, Object>) el.get("tags");

                        if (tags == null) continue;

                        Map<String, String> stringTags = new java.util.HashMap<>();
                        for (Map.Entry<String, Object> entry : tags.entrySet()) {
                            stringTags.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }

                        String name = stringTags.get("name");
                        if (name == null || name.isBlank()) {
                            name = stringTags.get("name:en");
                        }
                        if (name == null || name.isBlank()) {
                            name = stringTags.get("brand");
                        }
                        if (name == null || name.isBlank()) {
                            name = stringTags.get("operator");
                        }

                        if (name == null || name.isBlank()) continue;

                        if (logPrefix.contains("Hotels") && isInvalidHotel(name, stringTags)) continue;

                        Double latVal = parseCoordinate(el.get("lat"));
                        Double lonVal = parseCoordinate(el.get("lon"));

                        if ((latVal == null || lonVal == null) && el.containsKey("center")) {
                            Map<String, Object> center =
                                    (Map<String, Object>) el.get("center");
                            if (center != null) {
                                latVal = parseCoordinate(center.get("lat"));
                                lonVal = parseCoordinate(center.get("lon"));
                            }
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
                                        .name(name)
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
                    if (isTransientOverpassError(e)) {
                        hadTimeoutOrNetworkError = true;
                        log.warn("{} request failed on {} with transient error after {} ms: {}. Retrying...", logPrefix, overpassUrl, durationMs, e.getMessage());
                        if (retry < maxRetriesPerUrl && System.currentTimeMillis() + 1000 < deadline) {
                            try {
                                Thread.sleep(500L * (retry + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                    } else {
                        log.warn("{} request failed on {} after {} ms: {}", logPrefix, overpassUrl, durationMs, e.getMessage());
                        hadTimeoutOrNetworkError = true;
                    }
                    break; // try next URL
                }
            }
        }

        return new FetchResult(Collections.emptyList(), hadTimeoutOrNetworkError);
    }

    private boolean isTransientOverpassError(Exception e) {
        if (isTimeoutOrNetworkError(e)) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpStatusCodeException hs) {
            int status = hs.getStatusCode().value();
            return status == 502 || status == 503 || status == 504 || status == 429;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof org.springframework.web.client.HttpStatusCodeException hs) {
                int status = hs.getStatusCode().value();
                if (status == 502 || status == 503 || status == 504 || status == 429) return true;
            }
            cause = cause.getCause();
        }
        return false;
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
        String lowerName = name.toLowerCase(Locale.ROOT);

        List<String> keywords = List.of(
            "boys", "girls", "student", "pg", "residential"
        );

        for (String keyword : keywords) {
            if (lowerName.contains(keyword)) return true;
        }

        if (tags != null) {
            String shop = tags.get("shop");
            String amenity = tags.get("amenity");
            if (shop != null && !shop.isBlank()) return true;
            if ("restaurant".equals(amenity) || "cafe".equals(amenity) || "fast_food".equals(amenity)) {
                if (!lowerName.contains("hotel") && !lowerName.contains("resort") && !lowerName.contains("inn")) {
                    return true;
                }
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

    private Double parseCoordinate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
}
