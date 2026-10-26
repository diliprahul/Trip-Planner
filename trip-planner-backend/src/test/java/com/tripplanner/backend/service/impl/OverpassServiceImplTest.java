package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.util.PlaceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class OverpassServiceImplTest {

    private RestTemplate restTemplate;
    private OverpassServiceImpl overpassService;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        overpassService = new OverpassServiceImpl();
        // inject mocked restTemplate using reflection
        org.springframework.test.util.ReflectionTestUtils.setField(overpassService, "overpassRestTemplate", restTemplate);
    }

    @Test
    void testHttp502ErrorHandlingAndRetryFallback() {
        HttpStatusCodeException ics502 = Mockito.mock(HttpStatusCodeException.class);
        when(ics502.getStatusCode()).thenReturn(org.springframework.http.HttpStatusCode.valueOf(502));

        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ics502)
                .thenReturn(Map.of("elements", List.of(
                        Map.of("lat", 17.3850, "lon", 78.4867, "tags", Map.of("name", "Charminar", "tourism", "attraction"))
                )));

        List<PlaceResult> places = overpassService.getTouristPlaces(17.3850, 78.4867, 1);

        assertNotNull(places);
        assertEquals(1, places.size());
        assertEquals("Charminar", places.get(0).getName());
    }

    @Test
    void testTimeoutHandlingReturnsEmptyListGracefully() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        List<PlaceResult> places = overpassService.getTouristPlaces(17.3850, 78.4867, 1);

        assertNotNull(places);
        assertTrue(places.isEmpty());
    }

    @Test
    void testIndependentHotelProcessing() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("elements", List.of(
                        Map.of("lat", 17.3850, "lon", 78.4867, "tags", Map.of("name", "Grand Hotel", "tourism", "hotel"))
                )));

        List<PlaceResult> hotels = overpassService.getHotels(17.3850, 78.4867);

        assertNotNull(hotels);
        assertEquals(1, hotels.size());
        assertEquals("Grand Hotel", hotels.get(0).getName());
    }

    @Test
    void testHotelGenerationWithAmenityAndBuildingTags() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("elements", List.of(
                        Map.of("lat", 17.39, "lon", 78.49, "tags", Map.of("name", "Amenity Hotel", "amenity", "hotel")),
                        Map.of("lat", 17.40, "lon", 78.50, "tags", Map.of("name", "Building Hotel", "building", "hotel"))
                )));

        List<PlaceResult> hotels = overpassService.getHotels(17.3850, 78.4867);

        assertNotNull(hotels);
        assertEquals(2, hotels.size());
    }

    @Test
    void testHotelGenerationAlternativeNameTags() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("elements", List.of(
                        Map.of("lat", 17.39, "lon", 78.49, "tags", Map.of("name:en", "English Resort", "tourism", "resort")),
                        Map.of("lat", 17.40, "lon", 78.50, "tags", Map.of("brand", "Branded Inn", "tourism", "inn"))
                )));

        List<PlaceResult> hotels = overpassService.getHotels(17.3850, 78.4867);

        assertNotNull(hotels);
        assertEquals(2, hotels.size());
        assertEquals("English Resort", hotels.get(0).getName());
        assertEquals("Branded Inn", hotels.get(1).getName());
    }

    @Test
    void testHotelGenerationEmptyResultsAndFallback() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("elements", List.of()));

        List<PlaceResult> hotels = overpassService.getHotels(17.3850, 78.4867);

        assertNotNull(hotels);
        assertTrue(hotels.isEmpty());
    }

    @Test
    void testHotelGenerationHttp502AndTimeout() {
        HttpStatusCodeException ics502 = Mockito.mock(HttpStatusCodeException.class);
        when(ics502.getStatusCode()).thenReturn(org.springframework.http.HttpStatusCode.valueOf(502));

        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ics502)
                .thenThrow(new ResourceAccessException("Read timed out"));

        List<PlaceResult> hotels = overpassService.getHotels(17.3850, 78.4867);

        assertNotNull(hotels);
        assertTrue(hotels.isEmpty());
    }
}
