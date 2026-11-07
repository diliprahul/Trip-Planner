package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.geocoding.NominatimGeocodingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NominatimRetryAfterTest {
    private long retryAt(String value) throws Exception {
        NominatimGeocodingProvider provider = new NominatimGeocodingProvider(mock(RestTemplate.class));
        Method method = NominatimGeocodingProvider.class.getDeclaredMethod("retryAt", HttpHeaders.class);
        method.setAccessible(true);
        HttpHeaders headers = new HttpHeaders();
        if (value != null) headers.set("Retry-After", value);
        return (long) method.invoke(provider, headers);
    }

    @Test void numericRetryAfterIsRespected() throws Exception {
        long now = System.currentTimeMillis();
        assertTrue(retryAt("3") >= now + 2_900);
    }
    @Test void httpDateRetryAfterIsRespected() throws Exception {
        long target = System.currentTimeMillis() + 4_000;
        String header = ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(4).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(Math.abs(retryAt(header) - target) < 1_500);
    }
    @Test void missingRetryAfterUsesOneSecondMinimum() throws Exception {
        long now = System.currentTimeMillis();
        assertTrue(retryAt(null) >= now + 900);
    }
}
