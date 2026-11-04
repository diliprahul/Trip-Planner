package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.geocoding.GeocodingProvider;
import com.tripplanner.backend.util.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Primary
@Slf4j
public class GeocodingServiceImpl implements GeocodingService {
    private final List<GeocodingProvider> providers;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final long SUCCESS_TTL_MS = Duration.ofHours(24).toMillis();
    private static final long NEGATIVE_TTL_MS = Duration.ofHours(1).toMillis();

    private record CacheEntry(GeoLocation location, long expiresAtMs) {}

    public GeocodingServiceImpl(List<GeocodingProvider> providers) {
        List<GeocodingProvider> sorted = new ArrayList<>(providers != null ? providers : List.of());
        AnnotationAwareOrderComparator.sort(sorted);
        this.providers = Collections.unmodifiableList(sorted);
        log.info("GeocodingServiceImpl initialized with providers: {}", this.providers.stream().map(GeocodingProvider::getProviderName).toList());
    }

    @Override
    public GeoLocation geocodeCity(String city) {
        return lookup("CITY", city, null, city, false);
    }

    @Override
    public GeoLocation geocodeAttraction(String name, String destination) {
        String query = (name != null ? name : "") + (destination != null && !destination.isBlank() ? ", " + destination : "");
        return lookup("ATTRACTION", query, name, destination, true);
    }

    @Override
    public GeoLocation geocodeHotel(String name, String address, String destination) {
        String context = address == null || address.isBlank() ? destination : address + ", " + destination;
        String query = (name != null ? name : "") + ", " + context;
        return lookup("HOTEL", query, name, destination, true);
    }

    private GeoLocation lookup(String type, String query, String expectedName, String destination, boolean requireNamedPlace) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        String cacheKey = type + "|" + normalizedQuery + "|" + normalize(expectedName) + "|" + normalize(destination);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAtMs() > System.currentTimeMillis()) {
            log.debug("Geocoding cache hit for query='{}'", query);
            return cached.location();
        }

        GeoLocation result = null;
        String finalProvider = "None";
        long startTime = System.currentTimeMillis();
        boolean transientErrorEncountered = false;

        for (GeocodingProvider provider : providers) {
            String providerName = provider.getProviderName();
            if (!provider.isEnabled()) {
                log.debug("Skipping disabled geocoding provider: {}", providerName);
                continue;
            }

            try {
                long providerStart = System.currentTimeMillis();
                log.info("Attempting geocoding query='{}' with provider='{}'", query, providerName);
                GeoLocation loc = provider.geocode(query, expectedName, destination, requireNamedPlace);
                long duration = System.currentTimeMillis() - providerStart;

                if (loc != null && valid(loc.getLatitude(), loc.getLongitude())) {
                    result = loc;
                    finalProvider = providerName;
                    log.info("Successfully geocoded query='{}' using provider='{}' in {}ms -> {}", query, providerName, duration, result);
                    break;
                } else {
                    log.warn("Provider='{}' returned no valid or relevant result for query='{}'", providerName, query);
                }
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
                log.warn("Provider='{}' returned HTTP error {} for query", providerName, status);
                if (status == 429 || status >= 500) {
                    transientErrorEncountered = true;
                }
            } catch (ResourceAccessException e) {
                log.warn("Provider='{}' network/timeout error: {}", providerName, e.getMessage());
                transientErrorEncountered = true;
            } catch (Exception e) {
                log.warn("Provider='{}' threw exception: {}", providerName, e.getMessage());
                transientErrorEncountered = true;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        if (result != null) {
            cache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis() + SUCCESS_TTL_MS));
        } else if (!transientErrorEncountered) {
            cache.put(cacheKey, new CacheEntry(null, System.currentTimeMillis() + NEGATIVE_TTL_MS));
        } else {
            log.debug("Skipping negative cache for query='{}' due to transient provider error", query);
        }

        log.info("Geocoding complete for query='{}' (provider='{}', totalTime={}ms, resolved={})", query, finalProvider, totalDuration, result != null);
        return result;
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
