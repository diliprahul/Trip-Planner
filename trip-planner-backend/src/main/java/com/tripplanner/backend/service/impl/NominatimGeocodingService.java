package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.util.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NominatimGeocodingService implements GeocodingService {
    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final long SUCCESS_TTL_MS = Duration.ofHours(24).toMillis();
    private static final long NEGATIVE_TTL_MS = Duration.ofHours(1).toMillis();
    private static final long TRANSIENT_FAILURE_TTL_MS = Duration.ofMinutes(2).toMillis();
    private static final Object RATE_LOCK = new Object();
    private static long nextRequestAtMs;
    private final RestTemplate restTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private record CacheEntry(GeoLocation location, long expiresAtMs) { }

    private static final Map<String, GeoLocation> CURATED_ATTRACTIONS = Map.of(
            "charminar, hyderabad", new GeoLocation(17.3616, 78.4747),
            "golconda fort, hyderabad", new GeoLocation(17.3833, 78.4011),
            "ramoji film city, hyderabad", new GeoLocation(17.2543, 78.6826),
            "salar jung museum, hyderabad", new GeoLocation(17.3713, 78.4804),
            "chowmahalla palace, hyderabad", new GeoLocation(17.3592, 78.4704),
            "qutb shahi tombs, hyderabad", new GeoLocation(17.3917, 78.3953),
            "birla mandir, hyderabad", new GeoLocation(17.4053, 78.4691),
            "hussain sagar lake, hyderabad", new GeoLocation(17.4239, 78.4738)
    );

    @Override public GeoLocation geocodeCity(String city) { return lookup(city, null, city, false); }
    @Override public GeoLocation geocodeAttraction(String name, String destination) { return lookup(name + ", " + destination + ", India", name, destination, true); }
    @Override public GeoLocation geocodeHotel(String name, String address, String destination) {
        String context = address == null || address.isBlank() ? destination : address + ", " + destination;
        return lookup(name + ", " + context + ", India", name, destination, true);
    }

    private GeoLocation lookup(String query, String expectedName, String destination, boolean requireNamedPlace) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return null;

        // Check curated registry first if expectedName and destination are present
        if (expectedName != null && destination != null) {
            String normName = normalize(expectedName);
            String normDest = normalize(destination);
            String curatedKey = normName + ", " + normDest;
            GeoLocation curatedLoc = CURATED_ATTRACTIONS.get(curatedKey);
            if (curatedLoc != null && valid(curatedLoc.getLatitude(), curatedLoc.getLongitude())) {
                log.info("Curated registry exact matched name='{}' destination='{}' -> {}", expectedName, destination, curatedLoc);
                return curatedLoc;
            }
            // Flexible match: check each entry in CURATED_ATTRACTIONS where destination matches
            for (Map.Entry<String, GeoLocation> entry : CURATED_ATTRACTIONS.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(", " + normDest)) {
                    String attractionPart = key.substring(0, key.lastIndexOf(", "));
                    if (normName.contains(attractionPart) || attractionPart.contains(normName)) {
                        GeoLocation loc = entry.getValue();
                        if (loc != null && valid(loc.getLatitude(), loc.getLongitude())) {
                            log.info("Curated registry flexible matched name='{}' destination='{}' (key='{}') -> {}", expectedName, destination, key, loc);
                            return loc;
                        }
                    }
                }
            }
        }

        CacheEntry cached = cache.get(normalized);
        if (cached != null && cached.expiresAtMs() > System.currentTimeMillis()) return cached.location();
        String url = UriComponentsBuilder.fromHttpUrl(SEARCH_URL).queryParam("q", normalized)
                .queryParam("format", "jsonv2").queryParam("limit", 10).queryParam("addressdetails", 1).encode().toUriString();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                awaitRateLimit();
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "TripPlanner/1.0 (contact: dilip@example.com)");
                ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
                GeoLocation result = select(response.getBody(), expectedName, destination, requireNamedPlace);
                cache.put(normalized, new CacheEntry(result, System.currentTimeMillis() + (result == null ? NEGATIVE_TTL_MS : SUCCESS_TTL_MS)));
                log.info("Nominatim query='{}' result={}", normalized, result == null ? "no-valid-match" : "resolved");
                return result;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 403 || status == 404 || status == 429 || attempt == 1) {
                    return cacheFailure(normalized, "HTTP " + status);
                }
                sleep(retryDelay(e.getResponseHeaders(), attempt));
            } catch (Exception e) { return cacheFailure(normalized, e.getClass().getSimpleName()); }
        }
        return null;
    }
    private GeoLocation cacheFailure(String key, String reason) {
        log.warn("Nominatim query='{}' failed: {}", key, reason);
        cache.put(key, new CacheEntry(null, System.currentTimeMillis() + TRANSIENT_FAILURE_TTL_MS));
        return null;
    }
    private GeoLocation select(List<Map<String, Object>> candidates, String expectedName, String destination, boolean named) {
        if (candidates == null) return null;
        for (Map<String, Object> candidate : candidates) {
            String display = String.valueOf(candidate.getOrDefault("display_name", "")).toLowerCase(Locale.ROOT);
            if (named && (!matchesName(display, expectedName) || !matchesDestination(display, destination))) continue;
            Double lat = number(candidate.get("lat")); Double lon = number(candidate.get("lon"));
            if (valid(lat, lon)) return new GeoLocation(lat, lon);
        }
        return null;
    }
    private boolean matchesName(String display, String name) {
        List<String> significantTokens = Arrays.stream(normalize(name).split(" ")).filter(t -> t.length() > 3).toList();
        if (significantTokens.isEmpty()) return false;
        List<String> displayTokens = Arrays.stream(normalize(display).split(" ")).toList();
        long matchingTokens = significantTokens.stream()
                .filter(token -> displayTokens.stream().anyMatch(displayToken -> similar(token, displayToken)))
                .count();
        return matchingTokens * 2 >= significantTokens.size() + 1;
    }
    private boolean similar(String left, String right) {
        if (left.equals(right)) return true;
        if (left.length() < 5 || right.length() < 5) return false;
        return levenshtein(left, right) <= 1 && Math.max(left.length(), right.length()) >= 6;
    }
    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }
    private boolean matchesDestination(String display, String destination) { return normalize(destination).isBlank() || display.contains(normalize(destination)); }
    private Double number(Object value) { try { return value == null ? null : Double.valueOf(String.valueOf(value)); } catch (NumberFormatException e) { return null; } }
private boolean valid(Double lat, Double lon) {
    return lat != null && lon != null
            && lat >= -90 && lat <= 90
            && lon >= -180 && lon <= 180
            && !(lat == 0.0 && lon == 0.0);
}
private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim().replaceAll("\\s+", " "); }
    private void awaitRateLimit() { synchronized (RATE_LOCK) { long wait = nextRequestAtMs - System.currentTimeMillis(); if (wait > 0) sleep(wait); nextRequestAtMs = System.currentTimeMillis() + 1_000; } }
    private long retryDelay(HttpHeaders h, int attempt) { long header = 0; try { header = h == null || h.getFirst("Retry-After") == null ? 0 : Long.parseLong(h.getFirst("Retry-After")) * 1_000; } catch (NumberFormatException ignored) { } return Math.max(header, 1_000L << attempt) + new Random().nextInt(250); }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
