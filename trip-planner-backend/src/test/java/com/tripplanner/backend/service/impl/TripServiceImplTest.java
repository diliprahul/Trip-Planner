package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.GeminiService;
import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.service.WeatherService;
import com.tripplanner.backend.util.GeoLocation;
import com.tripplanner.backend.util.PlaceResult;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TripServiceImplTest {

    private TripServiceImpl tripService;
    private TripRepository tripRepo;
    private UserRepository userRepo;
    private DayPlanRepository dayRepo;
    private HotelSuggestionRepository hotelRepo;
    private GeocodingService geoService;
    private OverpassService overpassService;
    private WeatherService weatherService;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        tripRepo = Mockito.mock(TripRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        dayRepo = Mockito.mock(DayPlanRepository.class);
        hotelRepo = Mockito.mock(HotelSuggestionRepository.class);
        geoService = Mockito.mock(GeocodingService.class);
        overpassService = Mockito.mock(OverpassService.class);
        weatherService = Mockito.mock(WeatherService.class);
        geminiService = Mockito.mock(GeminiService.class);

        tripService = new TripServiceImpl(tripRepo, userRepo, dayRepo, hotelRepo, geoService, overpassService, weatherService, geminiService);
    }

    @Test
    void testProminentReligiousPlaceOutranksOrdinary() {
        PlaceResult ordinaryTemple = PlaceResult.builder()
                .name("Local Shrine")
                .tags(Map.of("amenity", "place_of_worship"))
                .build();

        PlaceResult prominentTemple = PlaceResult.builder()
                .name("Grand Temple")
                .tags(Map.of(
                        "amenity", "place_of_worship",
                        "wikidata", "Q11111",
                        "wikipedia", "en:Grand_Temple",
                        "heritage", "yes",
                        "religion", "hindu"
                ))
                .build();

        int ordinaryScore = tripService.getRelevanceScore(ordinaryTemple);
        int prominentScore = tripService.getRelevanceScore(prominentTemple);

        assertTrue(prominentScore > ordinaryScore,
                "Prominent temple score (" + prominentScore + ") should exceed ordinary temple score (" + ordinaryScore + ")");
    }

    @Test
    void testProminentHistoricPlaceOutranksOrdinary() {
        PlaceResult ordinaryHistoric = PlaceResult.builder()
                .name("Old Well")
                .tags(Map.of("historic", "yes"))
                .build();

        PlaceResult prominentHistoric = PlaceResult.builder()
                .name("Great Fort")
                .tags(Map.of(
                        "historic", "fort",
                        "wikidata", "Q22222",
                        "wikipedia", "en:Great_Fort",
                        "designation", "national_monument",
                        "heritage", "world_heritage"
                ))
                .build();

        int ordinaryScore = tripService.getRelevanceScore(ordinaryHistoric);
        int prominentScore = tripService.getRelevanceScore(prominentHistoric);

        assertTrue(prominentScore > ordinaryScore,
                "Prominent historic score (" + prominentScore + ") should exceed ordinary historic score (" + ordinaryScore + ")");
    }

    @Test
    void testModestCategoryBaseline() {
        PlaceResult park = PlaceResult.builder()
                .name("City Park")
                .tags(Map.of("leisure", "park"))
                .build();

        int score = tripService.getRelevanceScore(park);
        assertEquals(10, score, "Park with no metadata should receive only the modest baseline eligibility bonus of 10");
    }

    @Test
    void testMetadataRichnessCannotOverwhelmProminence() {
        PlaceResult ordinaryWithFullMetadata = PlaceResult.builder()
                .name("Ordinary Cafe Shop")
                .tags(Map.of(
                        "shop", "yes",
                        "official_name", "Shop Official",
                        "operator", "Operator Inc",
                        "opening_hours", "24/7",
                        "website", "http://example.com"
                ))
                .build();

        PlaceResult prominentWithoutExtraMeta = PlaceResult.builder()
                .name("Prominent Landmark")
                .tags(Map.of(
                        "historic", "monument",
                        "wikidata", "Q33333",
                        "wikipedia", "en:Landmark"
                ))
                .build();

        int metaScore = tripService.getRelevanceScore(ordinaryWithFullMetadata);
        int prominentScore = tripService.getRelevanceScore(prominentWithoutExtraMeta);

        assertTrue(prominentScore > metaScore,
                "Prominent landmark score (" + prominentScore + ") must exceed metadata-heavy ordinary place (" + metaScore + ")");
    }

    @Test
    void testNewCategoriesClassification() {
        PlaceResult reserve = PlaceResult.builder().name("Nature Reserve").tags(Map.of("leisure", "nature_reserve")).build();
        PlaceResult artwork = PlaceResult.builder().name("Art Statue").tags(Map.of("tourism", "artwork")).build();
        PlaceResult gallery = PlaceResult.builder().name("Art Gallery").tags(Map.of("tourism", "gallery")).build();
        PlaceResult cave = PlaceResult.builder().name("Limestone Cave").tags(Map.of("natural", "cave_entrance")).build();
        PlaceResult spring = PlaceResult.builder().name("Hot Spring").tags(Map.of("natural", "hot_spring")).build();

        assertTrue(tripService.getCategories(reserve).contains("LEISURE"));
        assertTrue(tripService.getCategories(artwork).contains("CULTURE"));
        assertTrue(tripService.getCategories(gallery).contains("CULTURE"));
        assertTrue(tripService.getCategories(cave).contains("NATURE"));
        assertTrue(tripService.getCategories(spring).contains("NATURE"));
    }

    @Test
    void testCategoryClassification() {
        PlaceResult historic = PlaceResult.builder().name("Fort").tags(Map.of("historic", "fort")).build();
        PlaceResult religious = PlaceResult.builder().name("Temple").tags(Map.of("amenity", "place_of_worship")).build();
        PlaceResult shopping = PlaceResult.builder().name("Mall").tags(Map.of("shop", "mall")).build();
        PlaceResult water = PlaceResult.builder().name("Lake").tags(Map.of("natural", "water")).build();
        PlaceResult nature = PlaceResult.builder().name("Peak").tags(Map.of("natural", "peak")).build();
        PlaceResult cultural = PlaceResult.builder().name("Museum").tags(Map.of("tourism", "museum")).build();
        PlaceResult entertainment = PlaceResult.builder().name("Theme Park").tags(Map.of("tourism", "theme_park")).build();
        PlaceResult leisure = PlaceResult.builder().name("Park").tags(Map.of("leisure", "park")).build();

        assertEquals("HISTORIC", tripService.categoryOf(historic));
        assertEquals("RELIGIOUS", tripService.categoryOf(religious));
        assertEquals("SHOPPING", tripService.categoryOf(shopping));
        assertEquals("WATER", tripService.categoryOf(water));
        assertEquals("NATURE", tripService.categoryOf(nature));
        assertEquals("CULTURE", tripService.categoryOf(cultural));
        assertEquals("ENTERTAINMENT", tripService.categoryOf(entertainment));
        assertEquals("LEISURE", tripService.categoryOf(leisure));
    }

    @Test
    void testMultiCategoryClassification() {
        PlaceResult multiCatPlace = PlaceResult.builder()
                .name("Historic Temple")
                .tags(Map.of("historic", "temple", "amenity", "place_of_worship"))
                .build();

        var cats = tripService.getCategories(multiCatPlace);
        assertTrue(cats.contains("HISTORIC"), "Should contain HISTORIC");
        assertTrue(cats.contains("RELIGIOUS"), "Should contain RELIGIOUS");
    }

    @Test
    void testNullAndEmptyCategoriesCompatibility() {
        PlaceResult place = PlaceResult.builder()
                .name("Fort")
                .tags(Map.of("historic", "fort"))
                .build();

        var cats = tripService.getCategories(place);
        assertFalse(cats.isEmpty());
    }

    @Test
    void testInvalidCategoryValueHandling() {
        PlaceResult place = PlaceResult.builder()
                .name("Fort")
                .tags(Map.of("historic", "fort"))
                .build();

        var cats = tripService.getCategories(place);
        assertFalse(cats.contains("INVALID_CATEGORY"));
    }

    @Test
    void persistsAndReturnsCoordinatesForEveryGeocodedFallbackAttraction() {
        User user = User.builder().id(1L).email("owner@example.com").name("Owner").build();
        Trip trip = Trip.builder()
                .id(906L)
                .user(user)
                .origin("Hyderabad")
                .destination("Vijayawada")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 10))
                .days(10)
                .latitude(16.5062)
                .longitude(80.6480)
                .build();
        List<Map<String, String>> attractions = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> Map.of("name", "Attraction " + i, "description", "Description " + i))
                .toList();
        AtomicReference<List<DayPlan>> savedPlans = new AtomicReference<>(List.of());

        when(tripRepo.findById(906L)).thenReturn(Optional.of(trip));
        when(userRepo.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(overpassService.getTouristPlaces(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        when(overpassService.getHotels(anyDouble(), anyDouble())).thenReturn(List.of());
        when(geminiService.generateItinerary("Vijayawada", 10)).thenReturn(attractions);
        when(geoService.geocodeAttraction(anyString(), eq("Vijayawada"))).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, String.class);
            int number = Integer.parseInt(query.replaceAll(".*Attraction (\\d+).*", "$1"));
            return new GeoLocation(16.40 + number / 100.0, 80.50 + number / 100.0);
        });
        doAnswer(invocation -> {
            savedPlans.set(List.copyOf(invocation.getArgument(0)));
            return savedPlans.get();
        }).when(dayRepo).saveAll(any());
        when(dayRepo.findByTripOrderByDayNumberAsc(trip)).thenAnswer(invocation -> savedPlans.get());
        when(hotelRepo.findByTrip(trip)).thenReturn(List.of());

        TripDetailResponse response = tripService.generateItinerary(906L, "owner@example.com");

        assertEquals(10, savedPlans.get().size());
        assertTrue(savedPlans.get().stream().allMatch(plan -> plan.getLatitude() != null && plan.getLongitude() != null));
        assertEquals(10, response.getDayPlans().size());
        assertTrue(response.getDayPlans().stream().allMatch(plan -> plan.getLatitude() != null && plan.getLongitude() != null));
        verify(geoService, times(10)).geocodeAttraction(anyString(), eq("Vijayawada"));
    }

    @Test
    void backfillsMissingCoordinatesOnlyWhenExplicitlyRequested() {
        User user = User.builder().id(1L).email("owner@example.com").name("Owner").build();
        Trip trip = Trip.builder()
                .id(906L).user(user).destination("Vijayawada").days(1)
                .latitude(16.5062).longitude(80.6480).build();
        DayPlan plan = DayPlan.builder().trip(trip).dayNumber(1)
                .placeName("Kanaka Durga Temple").description("Temple visit").build();

        when(tripRepo.findById(906L)).thenReturn(Optional.of(trip));
        when(userRepo.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(dayRepo.findByTripOrderByDayNumberAsc(trip)).thenReturn(List.of(plan));
        when(hotelRepo.findByTrip(trip)).thenReturn(List.of());
        when(geoService.geocodeAttraction("Kanaka Durga Temple", "Vijayawada"))
                .thenReturn(new GeoLocation(16.5150, 80.6100));

        TripDetailResponse response = tripService.getTripById(906L, "owner@example.com");
        assertNull(response.getDayPlans().get(0).getLatitude());
        var report = tripService.backfillCoordinates(906L, "owner@example.com", false);

        assertEquals(16.5150, plan.getLatitude());
        assertEquals(80.6100, plan.getLongitude());
        assertEquals(1, report.getUpdated());
        verify(dayRepo).save(plan);
    }
}
