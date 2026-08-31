package com.tripplanner.backend.service;

import com.tripplanner.backend.util.PlaceResult;
import java.util.List;

public interface OverpassService {

    List<PlaceResult> getTouristPlaces(double lat, double lon);

    List<PlaceResult> getHotels(double lat, double lon);

    // 🚧 Stub for future use (DO NOT REMOVE)
    default List<PlaceResult> getTransportAvailability(double lat, double lon) {
        return List.of();
    }
}
