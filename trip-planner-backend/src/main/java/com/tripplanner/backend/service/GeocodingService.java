package com.tripplanner.backend.service;

import com.tripplanner.backend.util.GeoLocation;

public interface GeocodingService {
    GeoLocation geocodeCity(String city);

    default GeoLocation geocodeAttraction(String name, String destination) {
        String query = (name != null ? name : "") + (destination != null && !destination.isBlank() ? ", " + destination : "");
        return geocodeCity(query);
    }

    default GeoLocation geocodeHotel(String name, String address, String destination) {
        String context = address == null || address.isBlank() ? destination : address + ", " + destination;
        String query = (name != null ? name : "") + ", " + context;
        return geocodeCity(query);
    }
}
