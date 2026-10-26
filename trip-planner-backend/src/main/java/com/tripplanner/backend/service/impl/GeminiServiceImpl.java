package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
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
            log.info("Calling Gemini API for destination: {} with {} days", destination, days);
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            Map<String, Object> response = callGemini(request);
            
            if (response == null || !response.containsKey("candidates")) {
                log.warn("Gemini API returned malformed or missing 'candidates' for destination: {}", destination);
                return Collections.emptyList();
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("Gemini API candidates list is empty for destination: {}", destination);
                return Collections.emptyList();
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            if (firstCandidate == null || !firstCandidate.containsKey("content")) {
                log.warn("Gemini API candidate content is missing for destination: {}", destination);
                return Collections.emptyList();
            }

            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                log.warn("Gemini API content parts are missing for destination: {}", destination);
                return Collections.emptyList();
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty() || !parts.get(0).containsKey("text")) {
                log.warn("Gemini API parts text is missing for destination: {}", destination);
                return Collections.emptyList();
            }

            String jsonText = (String) parts.get(0).get("text");
            if (jsonText == null || jsonText.isBlank()) {
                log.warn("Gemini API returned blank text for destination: {}", destination);
                return Collections.emptyList();
            }

            // Clean JSON text (remove markdown blocks if present)
            jsonText = jsonText.replaceAll("```json", "").replaceAll("```", "").trim();

            List<Map<String, String>> rawSuggestions = objectMapper.readValue(jsonText, new TypeReference<List<Map<String, String>>>() {});
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

            log.info("Gemini successfully generated {} valid tourist place suggestions for destination: {}", validSuggestions.size(), destination);
            return validSuggestions;

        } catch (HttpStatusCodeException e) {
            log.warn("Gemini itinerary request failed for {} with HTTP {} {} (model={})",
                    destination, e.getStatusCode().value(), e.getStatusText(), model);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Gemini itinerary request failed for {} using model={}: {}",
                    destination, model, e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Gemini API call or response parsing failed for destination {}: {}", destination, e.getMessage());
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
            log.info("Calling Gemini API for hotels in destination: {}", destination);
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            Map<String, Object> response = callGemini(request);
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

            jsonText = jsonText.replaceAll("```json", "").replaceAll("```", "").trim();
            List<Map<String, String>> rawHotels = objectMapper.readValue(jsonText, new TypeReference<List<Map<String, String>>>() {});
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
            log.info("Gemini successfully generated {} hotel suggestions for {}", validHotels.size(), destination);
            return validHotels;
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini hotel request failed for {} with HTTP {} {} (model={})",
                    destination, e.getStatusCode().value(), e.getStatusText(), model);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Gemini hotel request failed for {} using model={}: {}",
                    destination, model, e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Gemini hotel response parsing failed for {}: {}", destination, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> callGemini(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        return restTemplate.exchange(
                GEMINI_URL.formatted(model),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
        ).getBody();
    }
}
