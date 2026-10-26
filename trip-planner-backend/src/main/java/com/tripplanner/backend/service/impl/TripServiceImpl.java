package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.*;
import com.tripplanner.backend.entity.*;
import com.tripplanner.backend.repository.*;
import com.tripplanner.backend.service.*;
import com.tripplanner.backend.util.GeoLocation;
import com.tripplanner.backend.util.PlaceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final DayPlanRepository dayPlanRepository;
    private final HotelSuggestionRepository hotelSuggestionRepository;
    private final GeocodingService geocodingService;
    private final OverpassService overpassService;
    private final WeatherService weatherService;
    private final GeminiService geminiService;

    private static final String CURRENCY = "INR";

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank() || "anonymousUser".equals(userEmail)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required"
            );
        }
        String normalizedEmail = userEmail.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"
                ));
    }

    private void validateOwnership(Trip trip, String userEmail) {
        User user = resolveUser(userEmail);
        if (trip.getUser() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Trip not found"
            );
        }
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Access denied: You do not own this trip"
            );
        }
    }

    // =========================================================
    // CREATE TRIP
    // =========================================================
    @Override
    public TripDetailResponse createTrip(CreateTripRequest request, String userEmail) {
        log.info("CreateTripRequest: origin={}, destination={}, startDate={}, endDate={}, user={}",
            request.getOrigin(), request.getDestination(), request.getStartDate(), request.getEndDate(), userEmail);

        User user = resolveUser(userEmail);

        if (request.getEndDate() == null || request.getStartDate() == null) {
             throw new RuntimeException("Dates must not be null");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("End date cannot be before start date");
        }


        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;

        List<String> categories = request.getPlaceCategories() != null
                ? new ArrayList<>(request.getPlaceCategories())
                : new ArrayList<>();

        Trip trip = tripRepository.save(
                Trip.builder()
                        .user(user)
                        .origin(request.getOrigin())
                        .destination(request.getDestination())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .days((int) days)
                        .placeCategories(categories)
                        .build()
        );

        return map(trip, List.of(), List.of());
    }

    // =========================================================
    // GENERATE ITINERARY
    // =========================================================
    @Override
    public TripDetailResponse generateItinerary(Long tripId, String userEmail) {

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Trip not found"
                ));

        validateOwnership(trip, userEmail);

        dayPlanRepository.deleteByTrip(trip);
        hotelSuggestionRepository.deleteByTrip(trip);

        // ================= GEO =================
        if (trip.getLatitude() == null || trip.getLongitude() == null) {

            GeoLocation loc = geocodingService.geocodeCity(trip.getDestination());
            if (!isUsableLocation(loc)) {
                loc = geocodingService.geocodeCity(trip.getDestination() + ", India");
            }

            if (!isUsableLocation(loc)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Could not geocode destination: " + trip.getDestination()
                );
            }

            trip.setLatitude(loc.getLatitude());
            trip.setLongitude(loc.getLongitude());
            tripRepository.save(trip);
        }

        int days = Math.max(1, Optional.ofNullable(trip.getDays()).orElse(1));

        long osmStartedAt = System.nanoTime();
        CompletableFuture<List<PlaceResult>> placesFuture = CompletableFuture.supplyAsync(() -> fetchPlacesSafely(trip, days));
        CompletableFuture<List<PlaceResult>> hotelsFuture = CompletableFuture.supplyAsync(() -> fetchHotelsSafely(trip));

        List<PlaceResult> candidates = selectUsefulPlaces(placesFuture.join(), trip);

        if (candidates.isEmpty()) {
            log.info("Overpass returned no valid places for destination: {}. Attempting Gemini AI fallback...", trip.getDestination());
            try {
                List<Map<String, String>> geminiPlaces = geminiService.generateItinerary(trip.getDestination(), days);
                if (geminiPlaces != null && !geminiPlaces.isEmpty()) {
                    candidates = geminiPlaces.stream()
                            .filter(Objects::nonNull)
                            .map(gp -> {
                                GeoLocation placeLocation = geocodePlace(gp.get("name"), trip.getDestination());
                                return PlaceResult.builder()
                                        .name(gp.get("name"))
                                        .address(trip.getDestination())
                                        .latitude(placeLocation != null ? placeLocation.getLatitude() : null)
                                        .longitude(placeLocation != null ? placeLocation.getLongitude() : null)
                                        .tags(Map.of("description", gp.getOrDefault("description", "Popular attraction")))
                                        .build();
                            })
                            .filter(p -> p.getName() != null && !p.getName().isBlank())
                            .toList();
                    log.info("Gemini fallback provided {} attractions for {}", candidates.size(), trip.getDestination());
                }
            } catch (Exception e) {
                log.warn("Gemini fallback generation failed for {}: {}", trip.getDestination(), e.getMessage());
            }
        }

        List<DayPlan> plans = new ArrayList<>();
        for (int i = 0; i < Math.min(days, candidates.size()); i++) {
            PlaceResult place = candidates.get(i);
            String desc = place.getTags() != null && place.getTags().containsKey("description")
                    ? place.getTags().get("description")
                    : getDescription(place, trip.getDestination());
            plans.add(DayPlan.builder()
                    .trip(trip)
                    .dayNumber(i + 1)
                    .placeName(place.getName())
                    .description(desc)
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .build());
        }
        if (plans.isEmpty()) log.warn("No named tourist places or fallback suggestions were returned for {}", trip.getDestination());

        dayPlanRepository.saveAll(plans);

        // ================= HOTELS =================

        List<PlaceResult> hotelPlaces = hotelsFuture.join();
        log.info("OSM places and hotels for {} completed in {} ms", trip.getDestination(),
                (System.nanoTime() - osmStartedAt) / 1_000_000);

        List<HotelSuggestion> hotels =
                hotelPlaces.stream()
                        .filter(h -> h.getName() != null)
                        .filter(this::isAccommodation)
                        .limit(5)
                        .map(h -> HotelSuggestion.builder()
                                .trip(trip)
                                .name(h.getName())
                                .address(
                                        h.getAddress() != null
                                                ? h.getAddress()
                                                : trip.getDestination()
                                )
                                .latitude(h.getLatitude())
                                .longitude(h.getLongitude())
                                .build()
                        )
                        .toList();

        if (hotels.isEmpty()) {
            try {
                hotels = Optional.ofNullable(geminiService.generateHotels(trip.getDestination(), 5))
                        .orElse(List.of())
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(hotel -> hotel.get("name") != null && !hotel.get("name").isBlank())
                        .map(hotel -> HotelSuggestion.builder()
                                .trip(trip)
                                .name(hotel.get("name").trim())
                                .address(hotel.getOrDefault("address", trip.getDestination()))
                                .latitude(trip.getLatitude())
                                .longitude(trip.getLongitude())
                                .build())
                        .toList();
            } catch (Exception e) {
                log.warn("Gemini hotel fallback failed for {}: {}", trip.getDestination(), e.getMessage());
            }
        }

        hotelSuggestionRepository.saveAll(hotels);

        return getTripById(tripId, userEmail);
    }

    // =========================================================
    // FETCH
    // =========================================================
    @Override
    public TripDetailResponse getTripById(Long id, String userEmail) {

        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Trip not found"
                ));

        validateOwnership(trip, userEmail);

        List<DayPlan> plans = dayPlanRepository.findByTripOrderByDayNumberAsc(trip);
        return map(
                trip,
                plans,
                hotelSuggestionRepository.findByTrip(trip)
        );
    }

    @Override
    public List<TripDetailResponse> getMyTrips(String userEmail) {
        User user = resolveUser(userEmail);
        List<Trip> trips = tripRepository.findByUserOrderByCreatedAtDesc(user);
        if (trips == null || trips.isEmpty()) {
            return List.of();
        }
        return trips.stream()
                .filter(t -> t != null)
                .map(t -> map(
                        t,
                        dayPlanRepository.findByTripOrderByDayNumberAsc(t),
                        hotelSuggestionRepository.findByTrip(t)
                ))
                .toList();
    }

    @Override
    public void deleteTrip(Long id, String userEmail) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Trip not found"
                ));
        validateOwnership(trip, userEmail);
        dayPlanRepository.deleteByTrip(trip);
        hotelSuggestionRepository.deleteByTrip(trip);
        tripRepository.delete(trip);
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private List<PlaceResult> fetchPlacesSafely(Trip trip, int days) {
        try {
            return Optional.ofNullable(
                    overpassService.getTouristPlaces(
                            trip.getLatitude(),
                            trip.getLongitude(),
                            days
                    )
            ).orElse(List.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<PlaceResult> fetchHotelsSafely(Trip trip) {
        try {
            return Optional.ofNullable(overpassService.getHotels(trip.getLatitude(), trip.getLongitude()))
                    .orElse(List.of());
        } catch (Exception e) {
            log.warn("Hotel fetch failed for {}: {}", trip.getDestination(), e.getMessage());
            return List.of();
        }
    }

    private List<PlaceResult> selectUsefulPlaces(List<PlaceResult> places, Trip trip) {
        List<String> userCats = trip.getPlaceCategories();
        boolean hasFilter = userCats != null && !userCats.isEmpty()
                && !userCats.stream().anyMatch(c -> c.equalsIgnoreCase("sightseeing") || c.equalsIgnoreCase("all") || c.equalsIgnoreCase("all places"));
        Set<String> normalizedUserCats = hasFilter
                ? userCats.stream().filter(Objects::nonNull).map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toSet())
                : Collections.emptySet();

        Set<String> names = new HashSet<>();
        return places.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getName() != null && !p.getName().isBlank())
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> !isNoise(p.getName()))
                .filter(p -> !p.getName().toLowerCase(Locale.ROOT).contains("sightseeing"))
                .filter(p -> {
                    if (!hasFilter || normalizedUserCats.isEmpty()) return true;
                    Set<String> pCats = getCategories(p);
                    return pCats.stream().anyMatch(normalizedUserCats::contains);
                })
                .filter(p -> names.add(normalizeName(p.getName())))
                .sorted(Comparator.comparingInt(this::getRelevanceScore).reversed()
                        .thenComparing(p -> distanceSquared(trip, p)))
                .toList();
    }

    private boolean isAccommodation(PlaceResult place) {
        if (place.getTags() == null) return false;

        Map<String, String> tags = place.getTags();
        String tourism = tags.get("tourism");
        String amenity = tags.get("amenity");
        String building = tags.get("building");
        String name = place.getName() == null ? "" : place.getName().toLowerCase(Locale.ROOT);

        if (tags.containsKey("shop")) {
            return false;
        }

        if (name.contains("boys") || name.contains("girls") || name.contains("student") ||
            name.contains("students") || name.contains("ladies") || name.contains("women") ||
            name.contains("women's") || name.contains("men") || name.contains("men's") ||
            name.contains("working") || name.contains("workers") || name.contains("pg") ||
            name.contains("paying guest") || name.contains("residential")) {
            return false;
        }

        if ("hotel".equals(amenity) || "hotel".equals(building) || "yes".equals(tags.get("hotel"))) {
            return true;
        }

        if (tourism == null) {
            return name.contains("hotel") || name.contains("resort") || name.contains("inn") || name.contains("lodge") || name.contains("guest house") || name.contains("hostel") || name.contains("motel");
        }

        return tourism.contains("hotel") ||
               tourism.contains("guest_house") ||
               tourism.contains("motel") ||
               tourism.contains("hostel") ||
               tourism.contains("apartment") ||
               tourism.contains("resort") ||
               tourism.contains("inn") ||
               tourism.contains("lodge") ||
               tourism.contains("chalet") ||
               tourism.contains("alpine_hut") ||
               tourism.contains("wilderness_hut") ||
               "yes".equals(tourism);
    }

    private boolean isUsableLocation(GeoLocation location) {
        return location != null && location.getLatitude() != null && location.getLongitude() != null
                && !(location.getLatitude() == 0.0 && location.getLongitude() == 0.0);
    }

    private GeoLocation geocodePlace(String placeName, String destination) {
        if (placeName == null || placeName.isBlank() || destination == null || destination.isBlank()) {
            return null;
        }
        try {
            GeoLocation location = geocodingService.geocodeCity(placeName + ", " + destination);
            if (isUsableLocation(location)) {
                return location;
            }
        } catch (Exception e) {
            log.warn("Could not geocode generated attraction '{}' in {}: {}", placeName, destination,
                    e.getClass().getSimpleName());
            return null;
        }
        log.warn("Could not geocode generated attraction '{}' in {}", placeName, destination);
        return null;
    }

    private double distanceSquared(Trip trip, PlaceResult place) {
        double latitudeDelta = place.getLatitude() - trip.getLatitude();
        double longitudeDelta = place.getLongitude() - trip.getLongitude();
        return latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta;
    }

    private boolean isNoise(String name) {
        String n = name.toLowerCase();
        return n.contains("office") || n.contains("school") || n.contains("college") ||
                n.contains("bank") || n.contains("atm") || n.contains("toilet") ||
                n.contains("pharmacy") || n.contains("hospital") || n.contains("police") ||
                n.contains("bus stop") || n.contains("parking");
    }

    int getRelevanceScore(PlaceResult p) {
        int score = 0;
        Map<String, String> tags = p.getTags() != null ? p.getTags() : Collections.emptyMap();

        String historic = tags.getOrDefault("historic", "").toLowerCase(Locale.ROOT);
        String tourism = tags.getOrDefault("tourism", "").toLowerCase(Locale.ROOT);
        String amenity = tags.getOrDefault("amenity", "").toLowerCase(Locale.ROOT);
        String designation = tags.getOrDefault("designation", "").toLowerCase(Locale.ROOT);
        String heritage = tags.getOrDefault("heritage", "").toLowerCase(Locale.ROOT);
        String religion = tags.getOrDefault("religion", "").toLowerCase(Locale.ROOT);
        String denomination = tags.getOrDefault("denomination", "").toLowerCase(Locale.ROOT);

        if (tags.containsKey("wikidata")) {
            score += 100;
        }
        if (tags.containsKey("wikipedia")) {
            score += 90;
        }

        if (!heritage.isBlank() || tourism.contains("heritage")) {
            score += 60;
        }
        if (!designation.isBlank()) {
            score += 50;
        }
        if (!historic.isBlank()) {
            score += 40;
        }
        if (tourism.contains("museum") || tourism.contains("zoo") || tourism.contains("theme_park")) {
            score += 40;
        } else if (tourism.contains("attraction") || tourism.contains("viewpoint")) {
            score += 30;
        }
        if (amenity.equals("place_of_worship")) {
            score += 25;
            if (!religion.isBlank() || !denomination.isBlank()) {
                score += 15;
            }
        }

        if (tags.containsKey("official_name")) {
            score += 10;
        }
        if (tags.containsKey("operator")) {
            score += 10;
        }
        if (tags.containsKey("opening_hours")) {
            score += 10;
        }
        if (tags.containsKey("website") || tags.containsKey("contact:website")) {
            score += 15;
        }

        score += 10;

        return score;
    }

    private String getDescription(PlaceResult p, String city) {
        String cat = categoryOf(p);
        switch (cat) {
            case "HISTORIC":
                return "Explore the historical architecture and heritage of " + p.getName() + " in " + city;
            case "RELIGIOUS":
                return "A spiritual and architectural landmark in " + city;
            case "SHOPPING":
                return "A popular shopping and retail destination in " + city;
            case "WATER":
                return "Experience the stunning water body and scenic views in " + city;
            case "NATURE":
                return "Enjoy the natural scenery and peaks in " + city;
            case "CULTURAL":
            case "ENTERTAINMENT":
                return "Discover arts, culture, and entertainment at " + p.getName() + " in " + city;
            case "LEISURE":
                return "Relax and enjoy nature in this beautiful green space in " + city;
            default:
                return "Popular attraction in " + city;
        }
    }

    private List<BudgetEstimateDto> generateBudgetEstimates(int days, String destination) {
        List<BudgetEstimateDto> estimates = new ArrayList<>();

        double costMultiplier = 1.0;
        String d = destination.toLowerCase();
        if (d.contains("paris") || d.contains("france")) costMultiplier = 2.5;
        else if (d.contains("new york") || d.contains("usa")) costMultiplier = 3.0;
        else if (d.contains("london") || d.contains("uk")) costMultiplier = 2.0;

        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Low Budget")
                .minAccommodation((int)(1000 * days * costMultiplier))
                .maxAccommodation((int)(2500 * days * costMultiplier))
                .minTransport((int)(200 * days * costMultiplier))
                .maxTransport((int)(500 * days * costMultiplier))
                .minFood((int)(500 * days * costMultiplier))
                .maxFood((int)(1000 * days * costMultiplier))
                .minAttractions((int)(200 * days * costMultiplier))
                .maxAttractions((int)(1000 * days * costMultiplier))
                .minTotal((int)(1900 * days * costMultiplier))
                .maxTotal((int)(5000 * days * costMultiplier))
                .currency(CURRENCY)
                .assumptions("Estimated per traveler: basic accommodations, public transport, and local dining (Index: " + costMultiplier + ")")
                .build());

        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Standard Budget")
                .minAccommodation((int)(3000 * days * costMultiplier))
                .maxAccommodation((int)(7000 * days * costMultiplier))
                .minTransport((int)(1000 * days * costMultiplier))
                .maxTransport((int)(2500 * days * costMultiplier))
                .minFood((int)(1500 * days * costMultiplier))
                .maxFood((int)(3000 * days * costMultiplier))
                .minAttractions((int)(1000 * days * costMultiplier))
                .maxAttractions((int)(3000 * days * costMultiplier))
                .minTotal((int)(6500 * days * costMultiplier))
                .maxTotal((int)(15500 * days * costMultiplier))
                .currency(CURRENCY)
                .assumptions("Estimated per traveler: standard accommodations, local rides, and casual dining (Index: " + costMultiplier + ")")
                .build());

        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Premium Budget")
                .minAccommodation((int)(10000 * days * costMultiplier))
                .maxAccommodation((int)(30000 * days * costMultiplier))
                .minTransport((int)(3000 * days * costMultiplier))
                .maxTransport((int)(8000 * days * costMultiplier))
                .minFood((int)(4000 * days * costMultiplier))
                .maxFood((int)(10000 * days * costMultiplier))
                .minAttractions((int)(3000 * days * costMultiplier))
                .maxAttractions((int)(10000 * days * costMultiplier))
                .minTotal((int)(20000 * days * costMultiplier))
                .maxTotal((int)(58000 * days * costMultiplier))
                .currency(CURRENCY)
                .assumptions("Estimated per traveler: premium accommodations, private rides, and dining (Index: " + costMultiplier + ")")
                .build());

        return estimates;
    }

    private String normalizeName(String name) {
        return name.toLowerCase().trim();
    }

    public Set<String> getCategories(PlaceResult p) {
        Set<String> cats = new HashSet<>();
        Map<String, String> tags = p.getTags() != null ? p.getTags() : Collections.emptyMap();
        String historic = tags.getOrDefault("historic", "").toLowerCase(Locale.ROOT);
        String tourism = tags.getOrDefault("tourism", "").toLowerCase(Locale.ROOT);
        String amenity = tags.getOrDefault("amenity", "").toLowerCase(Locale.ROOT);
        String shop = tags.getOrDefault("shop", "").toLowerCase(Locale.ROOT);
        String natural = tags.getOrDefault("natural", "").toLowerCase(Locale.ROOT);
        String leisure = tags.getOrDefault("leisure", "").toLowerCase(Locale.ROOT);
        String name = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : "";

        if (!historic.isBlank() || tourism.contains("heritage") || name.contains("fort") || name.contains("palace") || name.contains("monument") || name.contains("tomb") || name.contains("castle") || name.contains("ruins")) {
            cats.add("HISTORIC");
        }
        if (amenity.equals("place_of_worship") || name.contains("temple") || name.contains("church") || name.contains("mosque") || name.contains("gurudwara") || name.contains("shrine") || name.contains("cathedral")) {
            cats.add("RELIGIOUS");
        }
        if (natural.equals("peak") || natural.equals("beach") || natural.equals("cave_entrance") || natural.equals("hot_spring") || name.contains("hill") || name.contains("mountain") || name.contains("valley")) {
            cats.add("NATURE");
        }
        if (natural.equals("water") || tags.containsKey("waterway") || name.contains("lake") || name.contains("river") || name.contains("waterfall") || name.contains("dam") || name.contains("reservoir")) {
            cats.add("WATER");
        }
        if (tourism.contains("museum") || tourism.contains("artwork") || tourism.contains("gallery") || amenity.equals("arts_centre") || amenity.equals("theatre") || name.contains("museum") || name.contains("gallery") || name.contains("art")) {
            cats.add("CULTURE");
        }
        if (!shop.isBlank() || shop.equals("mall") || shop.equals("department_store") || name.contains("mall") || name.contains("shopping")) {
            cats.add("SHOPPING");
        }
        if (amenity.equals("marketplace") || name.contains("market") || name.contains("bazaar")) {
            cats.add("MARKET");
        }
        if (tourism.contains("zoo") || tourism.contains("theme_park") || tourism.contains("attraction") || tourism.contains("viewpoint")) {
            cats.add("ENTERTAINMENT");
            if (name.contains("fort") || name.contains("palace") || name.contains("history")) cats.add("HISTORIC");
        }
        if (leisure.equals("park") || leisure.equals("garden") || leisure.equals("nature_reserve") || name.contains("garden") || name.contains("park") || name.contains("reserve")) {
            cats.add("LEISURE");
        }

        if (cats.isEmpty()) {
            cats.add("ENTERTAINMENT");
            cats.add("LEISURE");
        }
        return cats;
    }

    String categoryOf(PlaceResult p) {
        Set<String> cats = getCategories(p);
        if (!cats.isEmpty()) {
            return cats.iterator().next();
        }
        return "OTHER";
    }

    private String buildMapsUrl(String name, Double lat, Double lng, String city) {
        if (lat != null && lng != null) {
            return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng;
        }
        return "https://www.google.com/maps/search/?api=1&query="
                + (name + " " + city).replace(" ", "+");
    }

    private TripDetailResponse map(
            Trip t,
            List<DayPlan> plans,
            List<HotelSuggestion> hotels
    ) {

        int days = Math.max(1, Optional.ofNullable(t.getDays()).orElse(1));
        String destination = Optional.ofNullable(t.getDestination()).orElse("");
        List<HotelDto> hotelDtos = new ArrayList<>();

        hotels.stream()
                .limit(5)
                .forEach(h -> hotelDtos.add(
                        HotelDto.builder()
                                .name(h.getName())
                                .address(h.getAddress())
                                .latitude(h.getLatitude())
                                .longitude(h.getLongitude())
                                .searchUrl(
                                        buildMapsUrl(
                                                h.getName(),
                                                h.getLatitude(),
                                                h.getLongitude(),
                                                t.getDestination()
                                        )
                                )
                                .build()
                ));

        return TripDetailResponse.builder()
                .id(t.getId())
                .origin(t.getOrigin())
                .destination(t.getDestination())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .days(days)
                .latitude(t.getLatitude())
                .longitude(t.getLongitude())
                .placeCategories(t.getPlaceCategories())
                .dayPlans(
                        plans.stream()
                                .map(dp -> DayPlanDto.builder()
                                        .dayNumber(dp.getDayNumber())
                                        .placeName(dp.getPlaceName())
                                        .description(dp.getDescription())
                                        .latitude(dp.getLatitude())
                                        .longitude(dp.getLongitude())
                                        .mapsUrl(
                                                buildMapsUrl(
                                                        dp.getPlaceName(),
                                                        dp.getLatitude(),
                                                        dp.getLongitude(),
                                                        t.getDestination()
                                                )
                                        )
                                        .build())
                                .toList()
                )
                .hotels(hotelDtos)
                .budgetEstimates(generateBudgetEstimates(days, destination))
                .weatherResponse(
                        (t.getLatitude() != null && t.getLongitude() != null)
                                ? weatherService.getForecast(t.getLatitude(), t.getLongitude(), t.getStartDate(), t.getEndDate())
                                : null
                )
                .build();
    }

}
