package com.tripplanner.backend.service;

import com.tripplanner.backend.util.GeoLocation;

public interface GeocodingService {
    GeoLocation geocodeCity(String city);
}
