package com.tripplanner.backend.service.geocoding;

import com.tripplanner.backend.util.GeoLocation;

public interface GeocodingProvider {
    String getProviderName();
    boolean isEnabled();
    GeoLocation geocode(String query, String expectedName, String destination, boolean requireNamedPlace);
}
