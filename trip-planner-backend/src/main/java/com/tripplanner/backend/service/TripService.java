package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.dto.CoordinateBackfillReport;

import java.util.List;

public interface TripService {

    TripDetailResponse createTrip(CreateTripRequest request, String userEmail);

    TripDetailResponse generateItinerary(Long tripId, String userEmail);

    TripDetailResponse getTripById(Long id, String userEmail);

    List<TripDetailResponse> getMyTrips(String userEmail);

    void deleteTrip(Long id, String userEmail);

    CoordinateBackfillReport backfillCoordinates(Long id, String userEmail, boolean dryRun);
}
