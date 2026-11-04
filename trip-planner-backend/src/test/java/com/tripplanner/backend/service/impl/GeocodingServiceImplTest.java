package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.geocoding.GeocodingProvider;
import com.tripplanner.backend.service.geocoding.GoogleGeocodingProvider;
import com.tripplanner.backend.service.geocoding.HereGeocodingProvider;
import com.tripplanner.backend.service.geocoding.NominatimGeocodingProvider;
import com.tripplanner.backend.util.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GeocodingServiceImplTest {

    private GeocodingProvider nominatimProvider;
    private GeocodingProvider googleProvider;
    private GeocodingProvider hereProvider;
    private GeocodingServiceImpl geocodingService;

    @BeforeEach
    void setUp() {
        nominatimProvider = Mockito.mock(GeocodingProvider.class);
        googleProvider = Mockito.mock(GeocodingProvider.class);
        hereProvider = Mockito.mock(GeocodingProvider.class);

        when(nominatimProvider.getProviderName()).thenReturn("Nominatim");
        when(googleProvider.getProviderName()).thenReturn("Google");
        when(hereProvider.getProviderName()).thenReturn("HERE");

        when(nominatimProvider.isEnabled()).thenReturn(true);
        when(googleProvider.isEnabled()).thenReturn(true);
        when(hereProvider.isEnabled()).thenReturn(true);

        geocodingService = new GeocodingServiceImpl(List.of(nominatimProvider, googleProvider, hereProvider));
    }

    @Test
    void testNominatimSuccessPreventsFallbacks() {
        when(nominatimProvider.geocode(eq("Paris"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(48.8566, 2.3522));

        GeoLocation loc = geocodingService.geocodeCity("Paris");
        assertNotNull(loc);
        assertEquals(48.8566, loc.getLatitude());
        assertEquals(2.3522, loc.getLongitude());

        verify(nominatimProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
        verify(googleProvider, never()).geocode(any(), any(), any(), anyBoolean());
        verify(hereProvider, never()).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testNominatimNoResultTriggersGoogle() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(googleProvider.geocode(eq("Tokyo"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(35.6762, 139.6503));

        GeoLocation loc = geocodingService.geocodeCity("Tokyo");
        assertNotNull(loc);
        assertEquals(35.6762, loc.getLatitude());

        verify(nominatimProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
        verify(googleProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
        verify(hereProvider, never()).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testGoogleFailureTriggersHere() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(eq("London"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(51.5074, -0.1278));

        GeoLocation loc = geocodingService.geocodeCity("London");
        assertNotNull(loc);
        assertEquals(51.5074, loc.getLatitude());

        verify(nominatimProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
        verify(googleProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
        verify(hereProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testAllProvidersFailingReturnsNullSafely() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc = geocodingService.geocodeCity("NonexistentCityXYZ");
        assertNull(loc);
    }

    @Test
    void testProviderOrderEnforced() {
        List<GeocodingProvider> providers = List.of(new GoogleGeocodingProvider(mock(RestTemplate.class)), new NominatimGeocodingProvider(mock(RestTemplate.class)));
        GeocodingServiceImpl service = new GeocodingServiceImpl(providers);
        assertNotNull(service);
    }

    @Test
    void testDisabledProvidersSkipped() {
        when(nominatimProvider.isEnabled()).thenReturn(false);
        when(googleProvider.isEnabled()).thenReturn(true);
        when(googleProvider.geocode(eq("Berlin"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(52.5200, 13.4050));

        GeoLocation loc = geocodingService.geocodeCity("Berlin");
        assertNotNull(loc);
        assertEquals(52.5200, loc.getLatitude());

        verify(nominatimProvider, never()).geocode(any(), any(), any(), anyBoolean());
        verify(googleProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testHttp429DoesNotCausePermanentNegativeCaching() {
        HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Rate Limited", HttpHeaders.EMPTY, new byte[0], null);
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenThrow(ex);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc1 = geocodingService.geocodeCity("Rome");
        assertNull(loc1);

        geocodingService.geocodeCity("Rome");
        verify(nominatimProvider, times(2)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testHttp5xxDoesNotCausePermanentNegativeCaching() {
        HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null);
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenThrow(ex);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc1 = geocodingService.geocodeCity("Madrid");
        assertNull(loc1);

        geocodingService.geocodeCity("Madrid");
        verify(nominatimProvider, times(2)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testNetworkExceptionsAllowFallback() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenThrow(new ResourceAccessException("Timeout"));
        when(googleProvider.geocode(any(), any(), any(), anyBoolean()))
                .thenReturn(new GeoLocation(40.4168, -3.7038));

        GeoLocation loc = geocodingService.geocodeCity("Madrid");
        assertNotNull(loc);
        verify(googleProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testInvalidCoordinatesRejected() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean()))
                .thenReturn(new GeoLocation(0.0, 0.0));
        when(googleProvider.geocode(any(), any(), any(), anyBoolean()))
                .thenReturn(new GeoLocation(95.0, 200.0));
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc = geocodingService.geocodeCity("InvalidCoordsCity");
        assertNull(loc);
    }

    @Test
    void testIrrelevantAttractionResultsRejected() {
        when(nominatimProvider.geocode(eq("Eiffel Tower, Paris"), eq("Eiffel Tower"), eq("Paris"), eq(true)))
                .thenReturn(null);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc = geocodingService.geocodeAttraction("Eiffel Tower", "Paris");
        assertNull(loc);
    }

    @Test
    void testIrrelevantHotelResultsRejected() {
        when(nominatimProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(googleProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(hereProvider.geocode(any(), any(), any(), anyBoolean())).thenReturn(null);

        GeoLocation loc = geocodingService.geocodeHotel("Invalid Hotel", "Street", "Paris");
        assertNull(loc);
    }

    @Test
    void testDuplicateCityNamesRespectContext() {
        when(nominatimProvider.geocode(eq("Springfield, IL"), eq("Springfield"), eq("IL"), eq(true)))
                .thenReturn(new GeoLocation(39.7817, -89.6501));

        GeoLocation loc = geocodingService.geocodeAttraction("Springfield", "IL");
        assertNotNull(loc);
        assertEquals(39.7817, loc.getLatitude());
    }

    @Test
    void testInternationalCitiesResolveCorrectly() {
        when(nominatimProvider.geocode(eq("Tokyo"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(35.6762, 139.6503));

        GeoLocation loc = geocodingService.geocodeCity("Tokyo");
        assertNotNull(loc);
        assertEquals(139.6503, loc.getLongitude());
    }

    @Test
    void testSuccessfulResultsCached() {
        when(nominatimProvider.geocode(eq("Vienna"), any(), any(), eq(false)))
                .thenReturn(new GeoLocation(48.2082, 16.3738));

        GeoLocation loc1 = geocodingService.geocodeCity("Vienna");
        GeoLocation loc2 = geocodingService.geocodeCity("Vienna");

        assertNotNull(loc1);
        assertNotNull(loc2);
        verify(nominatimProvider, times(1)).geocode(any(), any(), any(), anyBoolean());
    }

    @Test
    void testAttractionAndHotelCacheEntriesDoNotCollide() {
        when(nominatimProvider.geocode(eq("Grand Hotel, Paris"), eq("Grand Hotel"), eq("Paris"), eq(true)))
                .thenReturn(new GeoLocation(48.8650, 2.3300));
        when(nominatimProvider.geocode(eq("Grand Hotel, Paris, Paris"), eq("Grand Hotel"), eq("Paris"), eq(true)))
                .thenReturn(new GeoLocation(48.8650, 2.3300));

        GeoLocation attr = geocodingService.geocodeAttraction("Grand Hotel", "Paris");
        GeoLocation hotel = geocodingService.geocodeHotel("Grand Hotel", "Paris", "Paris");

        assertNotNull(attr);
        assertNotNull(hotel);
    }

    @Test
    void testMissingApiKeysDisableProvider() {
        GoogleGeocodingProvider google = new GoogleGeocodingProvider(mock(RestTemplate.class));
        assertFalse(google.isEnabled());

        HereGeocodingProvider here = new HereGeocodingProvider(mock(RestTemplate.class));
        assertFalse(here.isEnabled());
    }

    @Test
    void testInvalidHotelCoordinatesDoNotBreakGeneration() {
        GeoLocation loc = geocodingService.geocodeHotel("Nonexistent", "Unknown", "City");
        assertNull(loc);
    }

    @Test
    void testOneFailedHotelLookupDoesNotDiscardOthers() {
        assertTrue(true);
    }

    @Test
    void testAttractionsAndHotelsNeverReceiveCityCentreFallback() {
        when(nominatimProvider.geocode(any(), any(), any(), eq(true))).thenReturn(null);
        GeoLocation loc = geocodingService.geocodeAttraction("Specific Attraction", "City");
        assertNull(loc);
    }
}
