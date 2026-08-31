package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.WikipediaImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WikipediaImageServiceImpl implements WikipediaImageService {

    private final RestTemplate restTemplate;

    private static final String FALLBACK_IMAGE =
            "https://upload.wikimedia.org/wikipedia/commons/d/d1/Image_not_available.png";

    @Override
    @SuppressWarnings("unchecked")
    public String getImageForPlace(String placeName) {

        try {
            String title = placeName.replace(" ", "_");
            String url =
                    "https://en.wikipedia.org/api/rest_v1/page/summary/" + title;

            Map<String, Object> response =
                    restTemplate.getForObject(url, Map.class);

            if (response == null) {
                return FALLBACK_IMAGE;
            }

            Map<String, Object> thumbnail =
                    (Map<String, Object>) response.get("thumbnail");

            if (thumbnail != null && thumbnail.get("source") != null) {
                return thumbnail.get("source").toString();
            }

        } catch (Exception e) {
            log.warn("Wikipedia image not found for {}", placeName);
        }

        // ✅ NEVER return null
        return FALLBACK_IMAGE;
    }
}
