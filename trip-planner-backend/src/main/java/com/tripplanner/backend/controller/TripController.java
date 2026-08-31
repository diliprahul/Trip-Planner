package com.tripplanner.backend.controller;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:*")
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripDetailResponse> createTrip(
            @RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.createTrip(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDetailResponse> getTrip(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @GetMapping
    public ResponseEntity<List<TripDetailResponse>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllTrips());
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<TripDetailResponse> generateItinerary(
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.generateItinerary(id));
    }

}
