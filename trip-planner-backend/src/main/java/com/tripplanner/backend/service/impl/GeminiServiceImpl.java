package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    private static final String GEMINI_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    @Override
    public List<Map<String, String>> generateItinerary(String destination, int days) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is missing. Falling back to empty list.");
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
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            Map<String, Object> response = restTemplate.postForObject(GEMINI_URL + apiKey, request, Map.class);
            
            if (response == null || !response.containsKey("candidates")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String jsonText = (String) parts.get(0).get("text");

            // Clean JSON text (remove markdown blocks if present)
            jsonText = jsonText.replaceAll("```json", "").replaceAll("```", "").trim();

            return objectMapper.readValue(jsonText, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
