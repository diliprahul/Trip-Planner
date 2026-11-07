package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.GeocodingUnavailableException;
import com.tripplanner.backend.entity.CityCoordinateCache;
import com.tripplanner.backend.repository.CityCoordinateCacheRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;

@Service
@Primary
@Slf4j
public class GeocodingServiceImpl implements GeocodingService {
    private final List<GeocodingProvider> providers;
    private final CityCoordinateCacheRepository cityCache;
    @Value("${geocoding.city-cache-ttl:30d}")
    private Duration cityCacheTtl = Duration.ofDays(30);
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final long SUCCESS_TTL_MS = Duration.ofHours(24).toMillis();
    private static final long NEGATIVE_TTL_MS = Duration.ofHours(1).toMillis();

    private record CacheEntry(GeoLocation location, long expiresAtMs) {}

    @Autowired
    public GeocodingServiceImpl(List<GeocodingProvider> providers, CityCoordinateCacheRepository cityCache) {
        List<GeocodingProvider> sorted = new ArrayList<>(providers != null ? providers : List.of());
        AnnotationAwareOrderComparator.sort(sorted);
        this.providers = Collections.unmodifiableList(sorted);
        this.cityCache = cityCache;
        log.info("GeocodingServiceImpl initialized with providers: {}", this.providers.stream().map(GeocodingProvider::getProviderName).toList());
    }

    /** Retained for callers that construct the service directly (including legacy tests). */
    public GeocodingServiceImpl(List<GeocodingProvider> providers) {
        this(providers, null);
    }

    @Override
    public GeoLocation geocodeCity(String city) {
        String key = normalize(city);
        if (!key.isEmpty()) {
            try {
                if (cityCache != null) {
                    CityCoordinateCache saved = cityCache.findById(key).orElse(null);
                    if (saved != null && saved.getResolvedAt() != null && saved.getResolvedAt().plus(cityCacheTtl).isAfter(Instant.now()))
                        return new GeoLocation(saved.getLatitude(), saved.getLongitude());
                    if (saved != null) cityCache.delete(saved);
                }
            } catch (RuntimeException e) { log.warn("City coordinate cache read/cleanup failed; continuing with providers: {}", e.getClass().getSimpleName()); }
        }
        GeoLocation location = lookup("CITY", city, null, city, false);
        if (location != null && !key.isEmpty() && cityCache != null) {
            try { cityCache.save(new CityCoordinateCache(key, location.getLatitude(), location.getLongitude(), Instant.now())); }
            catch (RuntimeException e) { log.warn("City coordinate cache write failed: {}", e.getClass().getSimpleName()); }
        }
        return location;
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
            log.debug("geocoding_stage={} outcome=cache_hit", type.toLowerCase(Locale.ROOT));
            return cached.location();
        }

        GeoLocation result = null;
        String finalProvider = "None";
        long startTime = System.currentTimeMillis();
        boolean transientErrorEncountered = false;
        boolean usableProviderAttempted = false;
        boolean allUsableProvidersTransient = true;

        for (GeocodingProvider provider : providers) {
            String providerName = provider.getProviderName();
            if (!provider.isEnabled()) {
                log.debug("Skipping disabled geocoding provider: {}", providerName);
                continue;
            }

            try {
                usableProviderAttempted = true;
                long providerStart = System.currentTimeMillis();
                log.info("geocoding_stage={} provider={} outcome=started", type.toLowerCase(Locale.ROOT), providerName);
                GeoLocation loc = provider.geocode(query, expectedName, destination, requireNamedPlace);
                long duration = System.currentTimeMillis() - providerStart;

                if (loc != null && valid(loc.getLatitude(), loc.getLongitude())) {
                    result = loc;
                    finalProvider = providerName;
                    log.info("geocoding_stage={} provider={} outcome=success duration_ms={}",
                            type.toLowerCase(Locale.ROOT), providerName, duration);
                    break;
                } else {
                    allUsableProvidersTransient = false;
                    log.warn("geocoding_stage={} provider={} outcome=unresolved duration_ms={}",
                            type.toLowerCase(Locale.ROOT), providerName, duration);
                }
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
                log.warn("Provider='{}' returned HTTP error {} for query", providerName, status);
                if (status == 429 || status >= 500) {
                    transientErrorEncountered = true;
                } else allUsableProvidersTransient = false;
            } catch (ResourceAccessException e) {
                log.warn("geocoding_stage={} provider={} outcome=network_error exception_type={}",
                        type.toLowerCase(Locale.ROOT), providerName, e.getClass().getSimpleName());
                transientErrorEncountered = true;
            } catch (Exception e) {
                log.warn("geocoding_stage={} provider={} outcome=exception exception_type={}",
                        type.toLowerCase(Locale.ROOT), providerName, e.getClass().getSimpleName());
                transientErrorEncountered = true;
                allUsableProvidersTransient = false;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        if (result != null) {
            cache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis() + SUCCESS_TTL_MS));
        } else if (!transientErrorEncountered) {
            cache.put(cacheKey, new CacheEntry(null, System.currentTimeMillis() + NEGATIVE_TTL_MS));
        } else {
            log.debug("geocoding_stage={} outcome=negative_cache_skipped reason=transient_provider_error",
                    type.toLowerCase(Locale.ROOT));
        }

        if (result == null && transientErrorEncountered && usableProviderAttempted && allUsableProvidersTransient)
            throw new GeocodingUnavailableException("Location lookup is temporarily unavailable. Please try again shortly.");

        log.info("geocoding_stage={} provider={} outcome={} duration_ms={}",
                type.toLowerCase(Locale.ROOT), finalProvider, result == null ? "unresolved" : "success", totalDuration);
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
