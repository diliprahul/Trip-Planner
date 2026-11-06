package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class EmailDeliveryServiceTest {
    @AfterEach void clearInterruptFlag() { Thread.interrupted(); }

    @Test void gmailSendsMessageAndReturnsSuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        assertTrue(new GmailEmailDeliveryService(java.util.Optional.of(sender)).send("to@example.com", "Subject", "Body"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertArrayEquals(new String[]{"to@example.com"}, captor.getValue().getTo());
        assertEquals("Subject", captor.getValue().getSubject());
        assertEquals("Body", captor.getValue().getText());
    }

    @Test void gmailExceptionReturnsFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("failure")).when(sender).send(any(SimpleMailMessage.class));
        assertFalse(new GmailEmailDeliveryService(java.util.Optional.of(sender)).send("to@example.com", "Subject", "Body"));
    }

    @Test void brevoTimeoutsRemainBounded() {
        assertEquals(Duration.ofSeconds(10), JdkBrevoTransport.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(15), JdkBrevoTransport.REQUEST_TIMEOUT);
    }

    @Test void localSmtpConfigurationDefinesConnectionReadAndWriteTimeouts() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        assertTrue(properties.contains("spring.mail.properties.mail.smtp.connectiontimeout=10000"));
        assertTrue(properties.contains("spring.mail.properties.mail.smtp.timeout=15000"));
        assertTrue(properties.contains("spring.mail.properties.mail.smtp.writetimeout=15000"));
    }

    private BrevoEmailDeliveryService brevo(BrevoTransport transport, String key, String fromEmail, String fromName) throws Exception {
        BrevoEmailDeliveryService service = new BrevoEmailDeliveryService(transport, new ObjectMapper());
        set(service, "apiKey", key); set(service, "fromEmail", fromEmail); set(service, "fromName", fromName);
        return service;
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    @Test void brevoSuccessfulApiResponseReturnsSuccessAndUsesConfiguredSender() throws Exception {
        BrevoTransport transport = mock(BrevoTransport.class);
        when(transport.post(anyString(), eq("secret"), anyString())).thenAnswer(call -> {
            String body = call.getArgument(2);
            var json = new ObjectMapper().readTree(body);
            assertEquals("tripplanner738@gmail.com", json.path("sender").path("email").asText());
            assertEquals("TripPlanner", json.path("sender").path("name").asText());
            assertEquals("to@example.com", json.path("to").get(0).path("email").asText());
            assertEquals("Subject", json.path("subject").asText());
            assertEquals("OTP text", json.path("textContent").asText());
            return 201;
        });
        assertTrue(brevo(transport, "secret", "tripplanner738@gmail.com", "TripPlanner").send("to@example.com", "Subject", "OTP text"));
    }

    @Test void brevoHttpErrorAndTransportExceptionReturnFailure() throws Exception {
        BrevoTransport transport = mock(BrevoTransport.class);
        when(transport.post(anyString(), anyString(), anyString())).thenReturn(401);
        assertFalse(brevo(transport, "secret", "from@example.com", "TripPlanner").send("to@example.com", "s", "t"));
        when(transport.post(anyString(), anyString(), anyString())).thenThrow(new IOException("failure"));
        assertFalse(brevo(transport, "secret", "from@example.com", "TripPlanner").send("to@example.com", "s", "t"));
    }

    @Test void brevoMissingConfigurationDoesNotCallTransport() throws Exception {
        BrevoTransport transport = mock(BrevoTransport.class);
        assertFalse(brevo(transport, "", "from@example.com", "TripPlanner").send("to@example.com", "s", "t"));
        verifyNoInteractions(transport);
    }

    @Test void brevoInterruptedRequestRestoresInterruptFlag() throws Exception {
        BrevoTransport transport = mock(BrevoTransport.class);
        when(transport.post(anyString(), anyString(), anyString())).thenThrow(new InterruptedException());
        assertFalse(brevo(transport, "secret", "from@example.com", "TripPlanner").send("to@example.com", "s", "t"));
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
