package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.service.GeminiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GeminiServiceImpl implements GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.gemini.api.key}")
    private String apiKey;

    @Value("${google.gemini.model:gemini-2.5-flash}")
    private String model;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private final Semaphore geminiConcurrency = new Semaphore(3, true);

    @Autowired
    public GeminiServiceImpl(@Qualifier("geminiRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, String>> generateItinerary(String destination, int days) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is missing (google.gemini.api.key / GEMINI_API_KEY is blank or unset). Falling back to empty list.");
            return Collections.emptyList();
        }

        String prompt = String.format(
            "Generate a %d-day tourist itinerary for %s. " +
            "For each day, suggest exactly one famous, recognizable, and real tourist attraction. " +
            "Provide a short, engaging description for each. " +
            "Return the result as a strict JSON array of objects with keys: 'day', 'name', 'description'. " +
            "Do not include any Markdown formatting or extra text. Only the JSON array.",
            days, destination
        );

        try {
            log.info("gemini_stage=itinerary outcome=requested day_count={}", days);
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            Map<String, Object> response = callGemini("itinerary", request);
            
            if (response == null || !response.containsKey("candidates")) {
                log.warn("Gemini stage=itinerary outcome=malformed_response");
                return Collections.emptyList();
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("Gemini stage=itinerary outcome=empty_candidates");
                return Collections.emptyList();
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            if (firstCandidate == null || !firstCandidate.containsKey("content")) {
                log.warn("Gemini stage=itinerary outcome=missing_content");
                return Collections.emptyList();
            }

            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                log.warn("Gemini stage=itinerary outcome=missing_parts");
                return Collections.emptyList();
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty() || !parts.get(0).containsKey("text")) {
                log.warn("Gemini stage=itinerary outcome=missing_text");
                return Collections.emptyList();
            }

            String jsonText = (String) parts.get(0).get("text");
            if (jsonText == null || jsonText.isBlank()) {
                log.warn("Gemini stage=itinerary outcome=blank_text");
                return Collections.emptyList();
            }

            List<Map<String, String>> rawSuggestions = parseJsonList(jsonText);
            if (rawSuggestions == null) {
                return Collections.emptyList();
            }

            // Validate suggestions: ensure non-null and name is present
            List<Map<String, String>> validSuggestions = new ArrayList<>();
            for (Map<String, String> item : rawSuggestions) {
                if (item != null) {
                    String name = item.get("name");
                    if (name != null && !name.isBlank()) {
                        Map<String, String> validItem = new HashMap<>();
                        validItem.put("day", item.getOrDefault("day", "1"));
                        validItem.put("name", name.trim());
                        validItem.put("description", item.getOrDefault("description", "Popular attraction"));
                        validSuggestions.add(validItem);
                    }
                }
            }

            log.info("gemini_stage=itinerary outcome=success result_count={}", validSuggestions.size());
            return validSuggestions;

        } catch (HttpStatusCodeException e) {
            log.warn("Gemini itinerary request failed with HTTP {} (model={})",
                    e.getStatusCode().value(), model);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Gemini itinerary request failed using model={}: {}",
                    model, e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Gemini itinerary call or response parsing failed: {}", e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, String>> generateHotels(String destination, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is missing. Skipping Gemini hotel fallback.");
            return Collections.emptyList();
        }

        String prompt = String.format(
            "Suggest up to %d well-known, real hotels or accommodations in %s. " +
            "Return the result as a strict JSON array of objects with keys: 'name', 'address'. " +
            "Do not include any Markdown formatting or extra text. Only the JSON array.",
            limit, destination
        );

        try {
            log.info("gemini_stage=hotels outcome=requested result_limit={}", limit);
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            Map<String, Object> response = callGemini("hotels", request);
            if (response == null || !response.containsKey("candidates")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String jsonText = (String) parts.get(0).get("text");
            if (jsonText == null || jsonText.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, String>> rawHotels = parseJsonList(jsonText);
            if (rawHotels == null) return Collections.emptyList();

            List<Map<String, String>> validHotels = new ArrayList<>();
            for (Map<String, String> item : rawHotels) {
                if (item != null) {
                    String name = item.get("name");
                    if (name != null && !name.isBlank()) {
                        Map<String, String> validItem = new HashMap<>();
                        validItem.put("name", name.trim());
                        validItem.put("address", item.getOrDefault("address", destination));
                        validHotels.add(validItem);
                    }
                }
            }
            log.info("gemini_stage=hotels outcome=success result_count={}", validHotels.size());
            return validHotels;
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini hotel request failed with HTTP {} (model={})",
                    e.getStatusCode().value(), model);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Gemini hotel request failed using model={}: {}",
                    model, e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Gemini hotel response parsing failed: {}", e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> callGemini(String stage, Map<String, Object> request) {
        long requestStarted = System.nanoTime();
        boolean acquired;
        try {
            acquired = geminiConcurrency.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.info("External request completed: service=gemini stage={} outcome=interrupted durationMs={}",
                    stage, elapsedMillis(requestStarted));
            return null;
        }
        if (!acquired) {
            log.info("External request completed: service=gemini stage={} outcome=concurrency_limit durationMs={}",
                    stage, elapsedMillis(requestStarted));
            return null;
        }
        try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        int maxAttempts = 3;
        long backoffMillis = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                Map<String, Object> response = restTemplate.exchange(
                        GEMINI_URL.formatted(model),
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        Map.class
                ).getBody();
                log.info("External request completed: service=gemini stage={} outcome=success attempt={} durationMs={}",
                        stage, attempt, elapsedMillis(startedAt));
                return response;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean isRetryable = (status == 503 || status == 429 || status == 502 || status == 504 || e.getStatusCode().is5xxServerError());
                log.info("External request completed: service=gemini stage={} outcome={} attempt={} durationMs={}",
                        stage, isRetryable && attempt < maxAttempts ? "retryable_http_error" : "http_error",
                        attempt, elapsedMillis(startedAt));
                if (isRetryable && attempt < maxAttempts) {
                    log.warn("Gemini API call failed with HTTP {} {}. Retrying in {} ms (attempt {}/{})",
                            status, e.getStatusText(), backoffMillis, attempt, maxAttempts);
                    try {
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Gemini backoff", ie);
                    }
                    backoffMillis *= 2;
                } else {
                    throw e;
                }
            } catch (RestClientException e) {
                log.info("External request completed: service=gemini stage={} outcome={} attempt={} durationMs={}",
                        stage, attempt < maxAttempts ? "retryable_exception" : "exception",
                        attempt, elapsedMillis(startedAt));
                if (attempt < maxAttempts) {
                    log.warn("Gemini API call failed with {}. Retrying in {} ms (attempt {}/{})",
                            e.getClass().getSimpleName(), backoffMillis, attempt, maxAttempts);
                    try {
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Gemini backoff", ie);
                    }
                    backoffMillis *= 2;
                } else {
                    throw e;
                }
            }
        }
        return null;
        } finally {
            geminiConcurrency.release();
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private List<Map<String, String>> parseJsonList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return Collections.emptyList();
        }

        // Clean markdown blocks
        jsonText = jsonText.replaceAll("(?s)```json\\s*", "")
                           .replaceAll("(?s)```\\s*", "")
                           .trim();

        // 1. Try direct deserialization as List<Map<String, String>>
        try {
            List<Map<String, String>> list = objectMapper.readValue(jsonText, new TypeReference<List<Map<String, String>>>() {});
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception e1) {
            // Try alternative formats below
        }

        // 2. Try reading consecutive JSON objects (e.g. {...}{...}) using readValues
        try {
            Iterator<Map<String, String>> iter = objectMapper.readerFor(new TypeReference<Map<String, String>>() {})
                    .readValues(jsonText);
            List<Map<String, String>> consecutiveList = new ArrayList<>();
            while (iter.hasNext()) {
                consecutiveList.add(iter.next());
            }
            if (consecutiveList.size() > 1) {
                return consecutiveList;
            }
            if (consecutiveList.size() == 1) {
                // Check if the whole text was just one object or if there's more
                // If it's a single object, let's verify if we should keep it or check wrapped maps
            }
        } catch (Exception e3) {
            // ignore
        }

        // 3. Try parsing as List or Map (wrapped or single object)
        try {
            Object parsed = objectMapper.readValue(jsonText, Object.class);
            if (parsed instanceof List) {
                List<?> rawList = (List<?>) parsed;
                List<Map<String, String>> result = new ArrayList<>();
                for (Object obj : rawList) {
                    if (obj instanceof Map) {
                        Map<String, String> map = new HashMap<>();
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null) {
                                map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                            }
                        }
                        result.add(map);
                    }
                }
                return result;
            } else if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;
                for (String key : List.of("itinerary", "attractions", "places", "days", "results", "data", "hotels")) {
                    if (map.containsKey(key) && map.get(key) instanceof List) {
                        List<?> rawList = (List<?>) map.get(key);
                        List<Map<String, String>> result = new ArrayList<>();
                        for (Object obj : rawList) {
                            if (obj instanceof Map) {
                                Map<String, String> itemMap = new HashMap<>();
                                for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                                    if (entry.getKey() != null && entry.getValue() != null) {
                                        itemMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                                    }
                                }
                                result.add(itemMap);
                            }
                        }
                        return result;
                    }
                }
                Map<String, String> singleMap = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        singleMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
                if (singleMap.containsKey("name") || singleMap.containsKey("day")) {
                    return List.of(singleMap);
                }
            }
        } catch (Exception e2) {
            // ignore
        }

        // 4. Try wrapping consecutive objects in brackets if not already array
        try {
            String wrapped = jsonText.trim();
            if (wrapped.startsWith("{") && wrapped.endsWith("}")) {
                wrapped = "[" + wrapped.replaceAll("}\\s*\\{", "},{") + "]";
                List<Map<String, String>> list = objectMapper.readValue(wrapped, new TypeReference<List<Map<String, String>>>() {});
                if (list != null && !list.isEmpty()) return list;
            }
        } catch (Exception e4) {
            log.warn("Failed to parse Gemini response as JSON list or objects: {}", e4.getClass().getSimpleName());
        }

        return Collections.emptyList();
    }
}
