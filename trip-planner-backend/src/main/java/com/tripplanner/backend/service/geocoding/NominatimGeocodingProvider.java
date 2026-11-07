package com.tripplanner.backend.service.geocoding;

import com.tripplanner.backend.util.GeoLocation;
import com.tripplanner.backend.service.impl.NominatimRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@Order(1)
@Slf4j
public class NominatimGeocodingProvider implements GeocodingProvider {
    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private final RestTemplate restTemplate;
    private final NominatimRateLimiter rateLimiter;

    @Autowired
    public NominatimGeocodingProvider(RestTemplate restTemplate, NominatimRateLimiter rateLimiter) {
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
    }

    /** Retained for direct legacy construction; production wiring uses the rate-limited constructor. */
    public NominatimGeocodingProvider(RestTemplate restTemplate) {
        this(restTemplate, null);
    }

    @Value("${geocoding.nominatim.enabled:true}")
    private boolean enabled;

    @Value("${geocoding.nominatim.user-agent:TripPlanner/1.0}")
    private String nominatimUserAgent;

    @Value("${geocoding.nominatim.contact-email:}")
    private String nominatimContactEmail;

    @jakarta.annotation.PostConstruct
    void configureUserAgent() {
        if (nominatimUserAgent == null || nominatimUserAgent.isBlank()) {
            nominatimUserAgent = "TripPlanner/1.0";
        }
        if (nominatimUserAgent.equals("TripPlanner/1.0") && nominatimContactEmail != null && !nominatimContactEmail.isBlank()) {
            if (!nominatimUserAgent.contains("(contact:")) {
                nominatimUserAgent += " (contact: " + nominatimContactEmail.trim() + ")";
            }
        }
        if (nominatimContactEmail == null || nominatimContactEmail.isBlank())
            log.warn("NOMINATIM_CONTACT_EMAIL is not configured; set a real monitored contact email before enabling production Nominatim requests");
    }

    private static final Map<String, GeoLocation> CURATED_CITIES;
    static {
        Map<String, GeoLocation> map = new HashMap<>();
        map.put("hyderabad", new GeoLocation(17.3850, 78.4867));
        map.put("vijayawada", new GeoLocation(16.5062, 80.6480));
        map.put("paris", new GeoLocation(48.8566, 2.3522));
        map.put("delhi", new GeoLocation(28.6139, 77.2090));
        map.put("mumbai", new GeoLocation(19.0760, 72.8777));
        map.put("varanasi", new GeoLocation(25.3176, 82.9739));
        map.put("agra", new GeoLocation(27.1767, 78.0081));
        map.put("new york", new GeoLocation(40.7128, -74.0060));
        map.put("london", new GeoLocation(51.5074, -0.1278));
        map.put("tokyo", new GeoLocation(35.6762, 139.6503));
        map.put("bengaluru", new GeoLocation(12.9716, 77.5946));
        map.put("bangalore", new GeoLocation(12.9716, 77.5946));
        CURATED_CITIES = Collections.unmodifiableMap(map);
    }

    private static final Map<String, GeoLocation> CURATED_ATTRACTIONS;
    static {
        Map<String, GeoLocation> map = new HashMap<>();
        map.put("charminar, hyderabad", new GeoLocation(17.3616, 78.4747));
        map.put("golconda fort, hyderabad", new GeoLocation(17.3833, 78.4011));
        map.put("ramoji film city, hyderabad", new GeoLocation(17.2543, 78.6826));
        map.put("salar jung museum, hyderabad", new GeoLocation(17.3713, 78.4804));
        map.put("chowmahalla palace, hyderabad", new GeoLocation(17.3592, 78.4704));
        map.put("qutb shahi tombs, hyderabad", new GeoLocation(17.3917, 78.3953));
        map.put("birla mandir, hyderabad", new GeoLocation(17.4053, 78.4691));
        map.put("hussain sagar lake, hyderabad", new GeoLocation(17.4239, 78.4738));
        map.put("kanaka durga temple, vijayawada", new GeoLocation(16.5151, 80.5826));
        map.put("undavalli caves, vijayawada", new GeoLocation(16.5015, 80.5847));
        map.put("prakasam barrage, vijayawada", new GeoLocation(16.5050, 80.6095));
        map.put("eiffel tower, paris", new GeoLocation(48.8584, 2.2945));
        map.put("louvre museum, paris", new GeoLocation(48.8606, 2.3376));
        map.put("notre dame de paris, paris", new GeoLocation(48.8529, 2.3500));
        map.put("bangalore palace, bengaluru", new GeoLocation(12.9988, 77.5922));
        map.put("bangalore palace, bangalore", new GeoLocation(12.9988, 77.5922));
        map.put("lalbagh botanical garden, bengaluru", new GeoLocation(12.9507, 77.5848));
        map.put("lalbagh botanical garden, bangalore", new GeoLocation(12.9507, 77.5848));
        map.put("cubbon park, bengaluru", new GeoLocation(12.9756, 77.5929));
        map.put("cubbon park, bangalore", new GeoLocation(12.9756, 77.5929));
        map.put("vidhana soudha, bengaluru", new GeoLocation(12.9797, 77.5906));
        map.put("vidhana soudha, bangalore", new GeoLocation(12.9797, 77.5906));
        map.put("tipu sultans summer palace, bengaluru", new GeoLocation(12.9587, 77.5750));
        map.put("tipu sultans summer palace, bangalore", new GeoLocation(12.9587, 77.5750));
        map.put("iskcon temple, bengaluru", new GeoLocation(13.0097, 77.5511));
        map.put("iskcon temple, bangalore", new GeoLocation(13.0097, 77.5511));
        map.put("bannerghatta national park, bengaluru", new GeoLocation(12.8000, 77.5770));
        map.put("bannerghatta national park, bangalore", new GeoLocation(12.8000, 77.5770));
        map.put("nandi hills, bengaluru", new GeoLocation(13.3702, 77.6835));
        map.put("nandi hills, bangalore", new GeoLocation(13.3702, 77.6835));
        CURATED_ATTRACTIONS = Collections.unmodifiableMap(map);
    }

    @Override
    public String getProviderName() {
        return "Nominatim";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public GeoLocation geocode(String query, String expectedName, String destination, boolean requireNamedPlace) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return null;

        if (!requireNamedPlace) {
            GeoLocation cityLoc = CURATED_CITIES.get(normalized);
            if (cityLoc != null && valid(cityLoc.getLatitude(), cityLoc.getLongitude())) {
                log.info("geocoding_provider=nominatim stage=curated_city outcome=matched");
                return cityLoc;
            }
        }

        if (expectedName != null && destination != null) {
            String normName = normalize(expectedName);
            String normDest = normalize(destination);
            String curatedKey = normName + ", " + normDest;
            GeoLocation curatedLoc = CURATED_ATTRACTIONS.get(curatedKey);
            if (curatedLoc != null && valid(curatedLoc.getLatitude(), curatedLoc.getLongitude())) {
                log.info("geocoding_provider=nominatim stage=curated_attraction outcome=matched");
                return curatedLoc;
            }
            for (Map.Entry<String, GeoLocation> entry : CURATED_ATTRACTIONS.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(", " + normDest)) {
                    String attractionPart = key.substring(0, key.lastIndexOf(", "));
                    if (normName.contains(attractionPart) || attractionPart.contains(normName)) {
                        GeoLocation loc = entry.getValue();
                        if (loc != null && valid(loc.getLatitude(), loc.getLongitude())) {
                            log.info("geocoding_provider=nominatim stage=curated_attraction outcome=matched");
                            return loc;
                        }
                    }
                }
            }
        }

        String url = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("q", normalized)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 10)
                .queryParam("addressdetails", 1)
                .encode()
                .toUriString();

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (rateLimiter == null) throw new IllegalStateException("Nominatim shared rate limiter is not configured");
                rateLimiter.awaitTurn();
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", nominatimUserAgent);
                ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
                GeoLocation result = select(response.getBody(), expectedName, destination, requireNamedPlace);
                log.info("geocoding_provider=nominatim outcome={}", result == null ? "unresolved" : "resolved");
                return result;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 429 || status >= 500) {
                    log.warn("Nominatim transient HTTP error {}", status);
                    if (attempt == 1) return null;
                    if (status == 429) {
                        long retryAt = retryAt(e.getResponseHeaders());
                        try { rateLimiter.deferUntil(retryAt); }
                        catch (RuntimeException coordinationFailure) { throw coordinationFailure; }
                    }
                } else if (status == 403 || status == 404 || attempt == 1) {
                    log.warn("Nominatim error {}", status);
                    return null;
                }
                if (status != 429) {
                    sleep(retryDelay(e.getResponseHeaders(), attempt));
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Nominatim request failed: {}", e.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private GeoLocation select(List<Map<String, Object>> candidates, String expectedName, String destination, boolean named) {
        if (candidates == null) return null;
        for (Map<String, Object> candidate : candidates) {
            String display = String.valueOf(candidate.getOrDefault("display_name", "")).toLowerCase(Locale.ROOT);
            if (named && (!matchesName(display, expectedName) || !matchesDestination(display, destination))) continue;
            Double lat = number(candidate.get("lat"));
            Double lon = number(candidate.get("lon"));
            if (valid(lat, lon)) return new GeoLocation(lat, lon);
        }
        return null;
    }

    private boolean matchesName(String display, String name) {
        if (name == null || name.isBlank()) return true;
        List<String> significantTokens = Arrays.stream(normalize(name).split(" ")).filter(t -> t.length() > 2).toList();
        if (significantTokens.isEmpty()) return true;
        List<String> displayTokens = Arrays.stream(normalize(display).split(" ")).toList();
        long matchingTokens = significantTokens.stream()
                .filter(token -> displayTokens.stream().anyMatch(displayToken -> similar(token, displayToken)))
                .count();
        return matchingTokens * 2 >= significantTokens.size() || matchingTokens > 0;
    }

    private boolean similar(String left, String right) {
        if (left.equals(right)) return true;
        if (left.length() < 4 || right.length() < 4) return left.contains(right) || right.contains(left);
        return levenshtein(left, right) <= 2;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private boolean matchesDestination(String display, String destination) {
        return destination == null || destination.isBlank() || display.contains(normalize(destination));
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

    private long retryAt(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst("Retry-After");
        if (value == null || value.isBlank()) return System.currentTimeMillis() + 1_000L;
        try { return System.currentTimeMillis() + Math.max(0, Long.parseLong(value)) * 1_000L; }
        catch (NumberFormatException ignored) {
            try { return Math.max(System.currentTimeMillis(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()); }
            catch (DateTimeParseException invalidDate) { return System.currentTimeMillis() + 1_000L; }
        }
    }

    private long retryDelay(HttpHeaders h, int attempt) {
        long header = 0;
        try {
            header = h == null || h.getFirst("Retry-After") == null ? 0 : Long.parseLong(h.getFirst("Retry-After")) * 1_000;
        } catch (NumberFormatException ignored) {}
        return Math.max(header, 1_000L << attempt) + new Random().nextInt(250);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
