package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GeminiServiceImplTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private GeminiServiceImpl geminiService;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        geminiService = new GeminiServiceImpl(restTemplate, objectMapper);
        ReflectionTestUtils.setField(geminiService, "model", "gemini-2.5-flash");
    }

    @Test
    void testMissingApiKeyReturnsEmptyList() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "");

        List<Map<String, String>> result = geminiService.generateItinerary("Hyderabad", 3);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void testNullApiKeyReturnsEmptyList() {
        ReflectionTestUtils.setField(geminiService, "apiKey", null);

        List<Map<String, String>> result = geminiService.generateItinerary("Hyderabad", 3);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void testApiFailureReturnsEmptyList() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("API down"));

        List<Map<String, String>> result = geminiService.generateItinerary("Hyderabad", 2);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMalformedResponseReturnsEmptyList() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");

        // Response missing candidates
        Map<String, Object> malformedResponse = Map.of("status", "ERROR");

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(malformedResponse));

        List<Map<String, String>> result = geminiService.generateItinerary("Hyderabad", 2);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testValidResponseReturnsSuggestions() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");

        String jsonArray = "[{\"day\":\"1\",\"name\":\"Charminar\",\"description\":\"Historic monument\"}," +
                           "{\"day\":\"2\",\"name\":\"Golconda Fort\",\"description\":\"Ancient fort\"}]";

        Map<String, Object> mockGeminiResponse = Map.of(
                "candidates", List.of(
                        Map.of(
                                "content", Map.of(
                                        "parts", List.of(
                                                Map.of("text", jsonArray)
                                        )
                                )
                        )
                )
        );

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(mockGeminiResponse));

        List<Map<String, String>> result = geminiService.generateItinerary("Hyderabad", 2);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Charminar", result.get(0).get("name"));
        assertEquals("Historic monument", result.get(0).get("description"));
        assertEquals("Golconda Fort", result.get(1).get("name"));
    }

    @Test
    void testValidHotelResponseReturnsSuggestions() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");
        String jsonArray = "[{\"name\":\"Taj Falaknuma Palace\",\"address\":\"Hyderabad\"}]";
        Map<String, Object> response = Map.of("candidates", List.of(
                Map.of("content", Map.of("parts", List.of(Map.of("text", jsonArray))))
        ));
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<Map<String, String>> result = geminiService.generateHotels("Hyderabad", 5);

        assertEquals(1, result.size());
        assertEquals("Taj Falaknuma Palace", result.get(0).get("name"));
        assertEquals("Hyderabad", result.get(0).get("address"));
    }

    @Test
    void testConsecutiveJsonObjectsResponseReturnsSuggestions() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");

        String consecutiveJson = "{\"day\":\"1\",\"name\":\"Dashashwamedh Ghat\",\"description\":\"Ganga Aarti\"}" +
                                 "{\"day\":\"2\",\"name\":\"Kashi Vishwanath Temple\",\"description\":\"Lord Shiva\"}";

        Map<String, Object> mockGeminiResponse = Map.of(
                "candidates", List.of(
                        Map.of(
                                "content", Map.of(
                                        "parts", List.of(
                                                Map.of("text", consecutiveJson)
                                        )
                                )
                        )
                )
        );

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(mockGeminiResponse));

        List<Map<String, String>> result = geminiService.generateItinerary("Varanasi", 2);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Dashashwamedh Ghat", result.get(0).get("name"));
        assertEquals("Kashi Vishwanath Temple", result.get(1).get("name"));
    }

    @Test
    void testHttp503RetriesAndReturnsEmptyList() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");

        org.springframework.web.client.HttpServerErrorException ex =
                new org.springframework.web.client.HttpServerErrorException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);

        long start = System.currentTimeMillis();
        List<Map<String, String>> result = geminiService.generateItinerary("Varanasi", 2);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(result.isEmpty());
        // Verify multiple attempts were made due to retry (3 attempts with exponential backoff)
        Mockito.verify(restTemplate, times(3)).exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }
}

