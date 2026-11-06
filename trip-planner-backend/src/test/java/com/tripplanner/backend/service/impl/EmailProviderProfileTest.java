package com.tripplanner.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.service.EmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EmailProviderProfileTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Providers.class);

    @Test void defaultProfileSelectsOnlyGmail() {
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(EmailDeliveryService.class).size());
            assertInstanceOf(GmailEmailDeliveryService.class, context.getBean(EmailDeliveryService.class));
        });
    }

    @Test void prodProfileSelectsOnlyBrevo() {
        runner.withPropertyValues("spring.profiles.active=prod", "BREVO_API_KEY=test-key")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(EmailDeliveryService.class).size());
                    assertInstanceOf(BrevoEmailDeliveryService.class, context.getBean(EmailDeliveryService.class));
                });
    }

    @Configuration
    @Import({GmailEmailDeliveryService.class, BrevoEmailDeliveryService.class})
    static class Providers {
        @Bean JavaMailSender javaMailSender() { return mock(JavaMailSender.class); }
        @Bean BrevoTransport brevoTransport() { return mock(BrevoTransport.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
