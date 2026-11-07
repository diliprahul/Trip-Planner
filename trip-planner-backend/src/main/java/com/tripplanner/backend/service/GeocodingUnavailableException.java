package com.tripplanner.backend.service;

public class GeocodingUnavailableException extends RuntimeException {
    public GeocodingUnavailableException(String message) { super(message); }
}
