package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.service.EmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailDeliveryService implements EmailDeliveryService {
    private static final String API_URI = "https://api.brevo.com/v3/smtp/email";
    private final BrevoTransport transport;
    private final ObjectMapper objectMapper;

    @Value("${BREVO_API_KEY:}") private String apiKey;
    @Value("${BREVO_FROM_EMAIL:tripplanner738@gmail.com}") private String fromEmail;
    @Value("${BREVO_FROM_NAME:TripPlanner}") private String fromName;

    @Override
    public boolean send(String recipient, String subject, String text) {
        long startedAt = System.nanoTime();
        String outcome = "failure";
        if (apiKey.isBlank() || fromEmail.isBlank() || fromName.isBlank()) {
            log.error("Brevo delivery failed. exceptionType=MissingConfiguration");
            log.info("External request completed: service=brevo stage=send outcome=missing_configuration durationMs={}",
                    elapsedMillis(startedAt));
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", recipient)),
                    "subject", subject,
                    "textContent", text));
            int status = transport.post(API_URI, apiKey, body);
            if (status >= 200 && status < 300) {
                outcome = "success";
                return true;
            }
            outcome = "http_error";
            log.error("Brevo delivery failed. httpStatus={}", status);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            outcome = "exception";
            log.error("Brevo delivery failed. exceptionType={}", exception.getClass().getSimpleName());
        } finally {
            log.info("External request completed: service=brevo stage=send outcome={} durationMs={}",
                    outcome, elapsedMillis(startedAt));
        }
        return false;
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
