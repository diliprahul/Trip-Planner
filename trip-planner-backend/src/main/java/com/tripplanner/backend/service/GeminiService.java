package com.tripplanner.backend.service;

import java.util.List;
import java.util.Map;

public interface GeminiService {
    List<Map<String, String>> generateItinerary(String destination, int days);
}
