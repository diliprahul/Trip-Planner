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
    private final DayPlanRepository dayPlanRepository;
    private final HotelSuggestionRepository hotelSuggestionRepository;
    private final GeocodingService geocodingService;
    private final OverpassService overpassService;
    private final WeatherService weatherService;

    private static final String CURRENCY = "INR";

    // =========================================================
    // CREATE TRIP
    // =========================================================
    @Override
    public TripDetailResponse createTrip(CreateTripRequest request) {
        log.info("CreateTripRequest: origin={}, destination={}, startDate={}, endDate={}", 
            request.getOrigin(), request.getDestination(), request.getStartDate(), request.getEndDate());

        if (request.getEndDate() == null || request.getStartDate() == null) {
             throw new RuntimeException("Dates must not be null");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("End date cannot be before start date");
        }

        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;

        Trip trip = tripRepository.save(
                Trip.builder()
                        .origin(request.getOrigin())
                        .destination(request.getDestination())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .days((int) days)
                        .placeCategories(request.getPlaceCategories())
                        .build()
        );

        return getTripById(trip.getId());
    }

    // =========================================================
    // GENERATE ITINERARY
    // =========================================================
    @Override
    public TripDetailResponse generateItinerary(Long tripId) {

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        dayPlanRepository.deleteByTrip(trip);
        hotelSuggestionRepository.deleteByTrip(trip);

        // ================= GEO =================
        if (trip.getLatitude() == null || trip.getLongitude() == null) {

            GeoLocation loc = geocodingService.geocodeCity(trip.getDestination());
            if (!isUsableLocation(loc)) {
                loc = geocodingService.geocodeCity(trip.getDestination() + ", India");
            }

            if (!isUsableLocation(loc)) {
                throw new RuntimeException("Unable to find a map location for the destination");
            }

            trip.setLatitude(loc.getLatitude());
            trip.setLongitude(loc.getLongitude());
            tripRepository.save(trip);
        }

        int days = Math.max(1, trip.getDays());

        // These independent OSM requests are the slow part of generation. Run them together,
        // while retaining the same real OSM-only data source for both sections.
        long osmStartedAt = System.nanoTime();
        CompletableFuture<List<PlaceResult>> placesFuture = CompletableFuture.supplyAsync(() -> fetchPlacesSafely(trip, days));
        CompletableFuture<List<PlaceResult>> hotelsFuture = CompletableFuture.supplyAsync(() -> fetchHotelsSafely(trip));

        // Use only named OpenStreetMap places, so no generated or generic places appear.
        List<PlaceResult> candidates = selectUsefulPlaces(placesFuture.join(), trip);
        
        List<DayPlan> plans = new ArrayList<>();
        for (int i = 0; i < Math.min(days, candidates.size()); i++) {
            PlaceResult place = candidates.get(i);
            plans.add(DayPlan.builder()
                    .trip(trip)
                    .dayNumber(i + 1)
                    .placeName(place.getName())
                    .description(getDescription(place, trip.getDestination()))
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .build());
        }
        if (plans.isEmpty()) log.warn("No named tourist places were returned for {}", trip.getDestination());

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

        hotelSuggestionRepository.saveAll(hotels);

        return getTripById(tripId);
    }

    // =========================================================
    // FETCH
    // =========================================================
    @Override
    public TripDetailResponse getTripById(Long id) {

        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        return map(
                trip,
                dayPlanRepository.findByTripOrderByDayNumberAsc(trip),
                hotelSuggestionRepository.findByTrip(trip)
        );
    }

    @Override
    public List<TripDetailResponse> getAllTrips() {
        return tripRepository.findAll()
                .stream()
                .map(t -> getTripById(t.getId()))
                .toList();
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
        String name = place.getName() == null ? "" : place.getName().toLowerCase(Locale.ROOT);

        // 1. Explicitly filter out non-accommodation based on name/amenity/shop
        if (name.contains("cafe") || name.contains("restaurant") || name.contains("tiffens") || 
            name.contains("bakery") || name.contains("bar")) {
            return false;
        }

        if (tags.containsKey("shop") || 
            (tags.containsKey("amenity") && (tags.get("amenity").contains("restaurant") || 
             tags.get("amenity").contains("cafe") || tags.get("amenity").contains("fast_food")))) {
            return false;
        }

        // 2. Reject residential/student/non-tourist accommodations based on name
        if (name.contains("boys") || name.contains("girls") || name.contains("student") || 
            name.contains("students") || name.contains("ladies") || name.contains("women") ||
            name.contains("women's") || name.contains("men") || name.contains("men's") || 
            name.contains("working") || name.contains("workers") || name.contains("pg") || 
            name.contains("paying guest") || name.contains("residential")) {
            return false;
        }

        // 3. Filter based on tourism tag
        if (tourism == null) return false;
        
        // 4. Accept genuine accommodation types including apartment, resort, inn, lodge
        return tourism.contains("hotel") ||
               tourism.contains("guest_house") ||
               tourism.contains("motel") ||
               tourism.contains("hostel") ||
               tourism.contains("apartment") ||
               tourism.contains("resort") ||
               tourism.contains("inn") ||
               tourism.contains("lodge");
    }

    private boolean isUsableLocation(GeoLocation location) {
        return location != null && location.getLatitude() != null && location.getLongitude() != null
                && !(location.getLatitude() == 0.0 && location.getLongitude() == 0.0);
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

        // 1. Core Prominence Signals (Wikidata / Wikipedia)
        if (tags.containsKey("wikidata")) {
            score += 100;
        }
        if (tags.containsKey("wikipedia")) {
            score += 90;
        }

        // 2. Landmark, Heritage & Historic Significance
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

        // 3. Metadata Completeness & Quality Signals (capped/modest)
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

        // 4. Modest Baseline Category Classification Signal
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
        
        // Simple cost index (relative to Hyderabad/India as 1.0)
        double costMultiplier = 1.0;
        String d = destination.toLowerCase();
        if (d.contains("paris") || d.contains("france")) costMultiplier = 2.5;
        else if (d.contains("new york") || d.contains("usa")) costMultiplier = 3.0;
        else if (d.contains("london") || d.contains("uk")) costMultiplier = 2.0;

        // Budget Tier
        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Budget")
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

        // Standard Tier
        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Standard")
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

        // Premium Tier
        estimates.add(BudgetEstimateDto.builder()
                .budgetCategory("Premium")
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

        if (!historic.isBlank() || tourism.contains("heritage")) {
            cats.add("HISTORIC");
        }
        if (amenity.equals("place_of_worship")) {
            cats.add("RELIGIOUS");
        }
        if (natural.equals("peak") || natural.equals("beach") || natural.equals("cave_entrance") || natural.equals("hot_spring")) {
            cats.add("NATURE");
        }
        if (natural.equals("water") || tags.containsKey("waterway")) {
            cats.add("WATER");
        }
        if (tourism.contains("museum") || tourism.contains("artwork") || tourism.contains("gallery") || amenity.equals("arts_centre") || amenity.equals("theatre")) {
            cats.add("CULTURE");
        }
        if (!shop.isBlank() || shop.equals("mall") || shop.equals("department_store")) {
            cats.add("SHOPPING");
        }
        if (amenity.equals("marketplace")) {
            cats.add("MARKET");
        }
        if (tourism.contains("zoo") || tourism.contains("theme_park") || tourism.contains("attraction") || tourism.contains("viewpoint")) {
            cats.add("ENTERTAINMENT");
        }
        if (leisure.equals("park") || leisure.equals("garden") || leisure.equals("nature_reserve")) {
            cats.add("LEISURE");
        }

        if (cats.isEmpty()) {
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

    // =========================================================
    // MAPPER
    // =========================================================
    private TripDetailResponse map(
            Trip t,
            List<DayPlan> plans,
            List<HotelSuggestion> hotels
    ) {

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
                .days(t.getDays())
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
                .budgetEstimates(generateBudgetEstimates(t.getDays(), t.getDestination()))
                .weatherResponse(
                        (t.getLatitude() != null && t.getLongitude() != null)
                                ? weatherService.getForecast(t.getLatitude(), t.getLongitude(), t.getStartDate(), t.getEndDate())
                                : null
                )
                .build();
    }

}
