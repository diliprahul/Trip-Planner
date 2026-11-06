package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.EmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class GmailEmailDeliveryService implements EmailDeliveryService {
    private final Optional<JavaMailSender> mailSender;

    @Override
    public boolean send(String recipient, String subject, String text) {
        try {
            if (mailSender.isEmpty()) {
                log.error("Gmail SMTP delivery failed. exceptionType=MissingConfiguration");
                return false;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(text);
            mailSender.get().send(message);
            return true;
        } catch (RuntimeException exception) {
            log.error("Gmail SMTP delivery failed. exceptionType={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
