package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.HotelSuggestion;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.util.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/** Database-only operations for trips. No external services are called from this bean. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripPersistenceService {
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final DayPlanRepository dayPlanRepository;
    private final HotelSuggestionRepository hotelSuggestionRepository;

    @Transactional
    public TripDataBundle createTrip(CreateTripRequest request, String userEmail) {
        return timed("create_trip", () -> {
            User user = resolveUser(userEmail);
            long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
            List<String> categories = request.getPlaceCategories() == null
                    ? new ArrayList<>() : new ArrayList<>(request.getPlaceCategories());
            Trip trip = tripRepository.save(Trip.builder().user(user).origin(request.getOrigin())
                    .destination(request.getDestination()).startDate(request.getStartDate())
                    .endDate(request.getEndDate()).days((int) days).placeCategories(categories).build());
            return bundle(trip, List.of(), List.of());
        });
    }

    @Transactional(readOnly = true)
    public TripDataBundle loadTrip(Long id, String userEmail) {
        return timed("load_trip", () -> {
            Trip trip = tripRepository.findById(id).orElseThrow(() -> notFound("Trip not found"));
            requireOwner(trip, userEmail);
            List<DayPlan> plans = dayPlanRepository.findByTripOrderByDayNumberAsc(trip);
            List<HotelSuggestion> hotels = hotelSuggestionRepository.findByTrip(trip);
            return bundle(trip, plans, hotels);
        });
    }

    @Transactional(readOnly = true)
    public List<Trip> loadMyTrips(String userEmail) {
        return timed("load_my_trips", () -> {
            User user = resolveUser(userEmail);
            return tripRepository.findByUserOrderByCreatedAtDesc(user).stream()
                    .filter(Objects::nonNull).map(this::copyTrip).toList();
        });
    }

    /** The trip row lock serializes replacements; all deletes/inserts/coordinate changes commit together. */
    @Transactional
    public TripDataBundle replaceGeneratedData(Long tripId, String userEmail, Double latitude, Double longitude,
                                               List<DayPlan> plans, List<HotelSuggestion> hotels) {
        return timed("persist_generated_itinerary", () -> {
            Trip trip = tripRepository.findByIdForUpdate(tripId)
                    .orElseThrow(() -> notFound("Trip not found"));
            requireOwner(trip, userEmail);

            trip.setLatitude(latitude);
            trip.setLongitude(longitude);
            tripRepository.save(trip);
            dayPlanRepository.deleteByTrip(trip);
            hotelSuggestionRepository.deleteByTrip(trip);

            plans.forEach(plan -> plan.setTrip(trip));
            hotels.forEach(hotel -> hotel.setTrip(trip));
            List<DayPlan> savedPlans = dayPlanRepository.saveAllAndFlush(plans);
            List<HotelSuggestion> savedHotels = hotelSuggestionRepository.saveAllAndFlush(hotels);
            return bundle(trip, savedPlans, savedHotels);
        });
    }

    @Transactional
    public void deleteTrip(Long id, String userEmail) {
        timed("delete_trip", () -> {
            Trip trip = tripRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Trip not found"));
            requireOwner(trip, userEmail);
            dayPlanRepository.deleteByTrip(trip);
            hotelSuggestionRepository.deleteByTrip(trip);
            tripRepository.delete(trip);
            return null;
        });
    }

    @Transactional
    public void applyCoordinateBackfill(Long id, String userEmail, Map<Long, GeoLocation> planLocations,
                                        Map<Long, GeoLocation> hotelLocations, Set<Long> clearHotelLocations) {
        timed("backfill_coordinates", () -> {
            Trip trip = tripRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Trip not found"));
            requireOwner(trip, userEmail);
            List<DayPlan> plans = dayPlanRepository.findByTripOrderByDayNumberAsc(trip);
            for (DayPlan plan : plans) {
                GeoLocation location = planLocations.get(plan.getId());
                if (location != null) {
                    plan.setLatitude(location.getLatitude());
                    plan.setLongitude(location.getLongitude());
                }
            }
            List<HotelSuggestion> hotels = hotelSuggestionRepository.findByTrip(trip);
            for (HotelSuggestion hotel : hotels) {
                GeoLocation location = hotelLocations.get(hotel.getId());
                if (location != null) {
                    hotel.setLatitude(location.getLatitude());
                    hotel.setLongitude(location.getLongitude());
                } else if (clearHotelLocations.contains(hotel.getId())) {
                    hotel.setLatitude(null);
                    hotel.setLongitude(null);
                }
            }
            dayPlanRepository.saveAllAndFlush(plans);
            hotelSuggestionRepository.saveAllAndFlush(hotels);
            return null;
        });
    }

    private User resolveUser(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> notFound("User not found"));
    }

    private void requireOwner(Trip trip, String email) {
        User user = resolveUser(email);
        if (trip.getUser() == null) throw notFound("Trip not found");
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this trip");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private TripDataBundle bundle(Trip trip, List<DayPlan> plans, List<HotelSuggestion> hotels) {
        Trip detachedTrip = copyTrip(trip);
        List<DayPlan> detachedPlans = plans.stream().map(plan -> DayPlan.builder().id(plan.getId())
                .trip(null).dayNumber(plan.getDayNumber()).placeName(plan.getPlaceName())
                .description(plan.getDescription()).latitude(plan.getLatitude()).longitude(plan.getLongitude()).build()).toList();
        List<HotelSuggestion> detachedHotels = hotels.stream().map(hotel -> HotelSuggestion.builder()
                .id(hotel.getId()).trip(null).name(hotel.getName()).address(hotel.getAddress())
                .latitude(hotel.getLatitude()).longitude(hotel.getLongitude()).rating(hotel.getRating()).build()).toList();
        return new TripDataBundle(detachedTrip, detachedPlans, detachedHotels);
    }

    private Trip copyTrip(Trip trip) {
        return Trip.builder().id(trip.getId()).origin(trip.getOrigin()).destination(trip.getDestination())
                .startDate(trip.getStartDate()).endDate(trip.getEndDate()).days(trip.getDays())
                .latitude(trip.getLatitude()).longitude(trip.getLongitude())
                .placeCategories(new ArrayList<>(trip.getPlaceCategories())).createdAt(trip.getCreatedAt()).build();
    }

    private <T> T timed(String operation, Supplier<T> action) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            log.info("database operation={} outcome={} duration_ms={}", operation, outcome,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
