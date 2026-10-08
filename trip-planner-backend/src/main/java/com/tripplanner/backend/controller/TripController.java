package com.tripplanner.backend.controller;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:*", allowCredentials = "true")
public class TripController {

    private final TripService tripService;

    private String getEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Authentication required");
        }
        return authentication.getName();
    }

    @PostMapping
    public ResponseEntity<TripDetailResponse> createTrip(
            @RequestBody CreateTripRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(tripService.createTrip(request, getEmail(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDetailResponse> getTrip(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(tripService.getTripById(id, getEmail(authentication)));
    }

    @GetMapping("/my")
    public ResponseEntity<List<TripDetailResponse>> getMyTrips(Authentication authentication) {
        return ResponseEntity.ok(tripService.getMyTrips(getEmail(authentication)));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<TripDetailResponse> generateItinerary(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(tripService.generateItinerary(id, getEmail(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(
            @PathVariable Long id,
            Authentication authentication) {
        tripService.deleteTrip(id, getEmail(authentication));
        return ResponseEntity.ok().build();
    }
}
