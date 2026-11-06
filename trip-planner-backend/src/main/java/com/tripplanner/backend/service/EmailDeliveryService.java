package com.tripplanner.backend.service;

public interface EmailDeliveryService {
    boolean send(String recipient, String subject, String text);
}
