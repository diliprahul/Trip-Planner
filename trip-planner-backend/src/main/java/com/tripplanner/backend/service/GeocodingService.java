package com.tripplanner.backend.service;

import com.tripplanner.backend.util.GeoLocation;

public interface GeocodingService {
    GeoLocation geocodeCity(String city);

    default GeoLocation geocodeAttraction(String name, String destination) {
        return geocodeCity(name + ", " + destination + ", India");
    }

    default GeoLocation geocodeHotel(String name, String address, String destination) {
        String context = address == null || address.isBlank() ? destination : address + ", " + destination;
        return geocodeCity(name + ", " + context + ", India");
    }
}
