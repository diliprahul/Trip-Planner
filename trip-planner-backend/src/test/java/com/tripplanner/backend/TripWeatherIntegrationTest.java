package com.tripplanner.backend;

import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.HotelSuggestion;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.WeatherService;
import com.tripplanner.backend.service.TripService;
import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.GeminiService;
import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.service.impl.TripPersistenceService;
import com.tripplanner.backend.util.GeoLocation;
import com.tripplanner.backend.util.PlaceResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripWeatherIntegrationTest {
    private static final String OWNER_EMAIL = "weather-owner@example.com";
    private static final String OTHER_EMAIL = "weather-other@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private DayPlanRepository dayPlanRepository;
    @Autowired private HotelSuggestionRepository hotelSuggestionRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private TripService tripService;
    @Autowired private TripPersistenceService tripPersistenceService;

    @MockBean private WeatherService weatherService;
    @MockBean private OverpassService overpassService;
    @MockBean private GeocodingService geocodingService;
    @MockBean private GeminiService geminiService;

    private Long tripId;

    @BeforeEach
    void setUp() {
        pendingRegistrationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        dayPlanRepository.deleteAll();
        hotelSuggestionRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
        reset(weatherService);
        reset(overpassService, geocodingService, geminiService);

        User owner = userRepository.save(User.builder()
                .name("Weather Owner")
                .email(OWNER_EMAIL)
                .password("not-used")
                .provider("LOCAL")
                .build());
        tripId = tripRepository.save(Trip.builder()
                .user(owner)
                .origin("Vijayawada")
                .destination("Bengaluru")
                .startDate(LocalDate.of(2026, 11, 10))
                .endDate(LocalDate.of(2026, 11, 12))
                .days(3)
                .latitude(12.9716)
                .longitude(77.5946)
                .placeCategories(List.of("HISTORIC"))
                .build()).getId();
    }

    @Test
    void tripDetailsLoadWithoutWeatherProvider() throws Exception {
        when(weatherService.getForecast(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Weather service unavailable"));

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .with(user(OWNER_EMAIL))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Bengaluru"))
                .andExpect(jsonPath("$.weatherResponse").value(org.hamcrest.Matchers.nullValue()));

        verifyNoInteractions(weatherService);
    }

    @Test
    void weatherEndpointReportsProviderFailureWithoutAffectingTripDetails() throws Exception {
        when(weatherService.getForecast(12.9716, 77.5946,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)))
                .thenReturn(WeatherResponseDto.builder()
                        .status(WeatherResponseDto.Status.API_ERROR)
                        .message("Weather information could not be loaded at the moment.")
                        .build());

        mockMvc.perform(get("/api/trips/{tripId}/weather", tripId)
                        .with(user(OWNER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("API_ERROR"));

        verify(weatherService).getForecast(12.9716, 77.5946,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
    }

    @Test
    void weatherEndpointDoesNotExposeAnotherUsersTrip() throws Exception {
        mockMvc.perform(get("/api/trips/{tripId}/weather", tripId)
                        .with(user(OTHER_EMAIL)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(weatherService);
    }

    @Test
    void generationPersistsPlacesAndHotelsAndReturnsSavedTrip() {
        PlaceResult place = PlaceResult.builder().name("Central Museum").latitude(12.98).longitude(77.60)
                .tags(Map.of("tourism", "museum", "historic", "monument")).build();
        PlaceResult hotel = PlaceResult.builder().name("Central Hotel").address("Market Road")
                .latitude(12.99).longitude(77.61).tags(Map.of("tourism", "hotel")).build();
        when(overpassService.getTouristPlaces(12.9716, 77.5946, 3)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(place);
        });
        when(overpassService.getHotels(12.9716, 77.5946)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(hotel);
        });

        var response = tripService.generateItinerary(tripId, OWNER_EMAIL);

        assertEquals(1, response.getDayPlans().size());
        assertEquals("Central Museum", response.getDayPlans().get(0).getPlaceName());
        assertEquals(1, response.getHotels().size());
        assertEquals("Central Hotel", response.getHotels().get(0).getName());
        assertEquals(1, dayPlanRepository.findByTripOrderByDayNumberAsc(tripRepository.findById(tripId).orElseThrow()).size());
        assertEquals(1, hotelSuggestionRepository.findByTrip(tripRepository.findById(tripId).orElseThrow()).size());
    }

    @Test
    void cityGeocodingFailureLeavesPreviouslySavedItineraryUntouched() {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        trip.setLatitude(null);
        trip.setLongitude(null);
        tripRepository.save(trip);
        dayPlanRepository.save(DayPlan.builder().trip(trip).dayNumber(1).placeName("Existing Place").description("Keep me").build());
        hotelSuggestionRepository.save(HotelSuggestion.builder().trip(trip).name("Existing Hotel").address("Address").build());
        when(geocodingService.geocodeCity("Bengaluru")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("provider unavailable");
        });

        assertThrows(IllegalStateException.class, () -> tripService.generateItinerary(tripId, OWNER_EMAIL));

        assertEquals("Existing Place", dayPlanRepository.findByTripOrderByDayNumberAsc(trip).get(0).getPlaceName());
        assertEquals("Existing Hotel", hotelSuggestionRepository.findByTrip(trip).get(0).getName());
    }

    @Test
    void persistenceFailureRollsBackDeletesPlacesHotelsAndCoordinateUpdates() {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        DayPlan previousPlan = dayPlanRepository.save(DayPlan.builder().trip(trip).dayNumber(1)
                .placeName("Existing Place").description("Keep me").build());
        HotelSuggestion previousHotel = hotelSuggestionRepository.save(HotelSuggestion.builder().trip(trip)
                .name("Existing Hotel").address("Old Address").build());
        DayPlan replacementPlan = DayPlan.builder().dayNumber(1).placeName("New Place")
                .description("New description").build();
        HotelSuggestion invalidHotel = HotelSuggestion.builder().name("New Hotel").address("x".repeat(1100)).build();

        assertThrows(RuntimeException.class, () -> tripPersistenceService.replaceGeneratedData(tripId, OWNER_EMAIL,
                13.0, 78.0, List.of(replacementPlan), List.of(invalidHotel)));

        Trip unchangedTrip = tripRepository.findById(tripId).orElseThrow();
        assertEquals(12.9716, unchangedTrip.getLatitude());
        assertEquals("Existing Place", dayPlanRepository.findByTripOrderByDayNumberAsc(unchangedTrip).get(0).getPlaceName());
        assertEquals("Existing Hotel", hotelSuggestionRepository.findByTrip(unchangedTrip).get(0).getName());
        assertTrue(dayPlanRepository.findById(previousPlan.getId()).isPresent());
        assertTrue(hotelSuggestionRepository.findById(previousHotel.getId()).isPresent());
    }

    @Test
    void concurrentGenerationPersistenceSerializesCompleteReplacements() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                await(start);
                tripPersistenceService.replaceGeneratedData(tripId, OWNER_EMAIL, 21.0, 81.0,
                        List.of(DayPlan.builder().dayNumber(1).placeName("Place A").description("A").build()),
                        List.of(HotelSuggestion.builder().name("Hotel A").address("A").build()));
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                tripPersistenceService.replaceGeneratedData(tripId, OWNER_EMAIL, 22.0, 82.0,
                        List.of(DayPlan.builder().dayNumber(1).placeName("Place B").description("B").build()),
                        List.of(HotelSuggestion.builder().name("Hotel B").address("B").build()));
            });
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        Trip persistedTrip = tripRepository.findById(tripId).orElseThrow();
        List<DayPlan> plans = dayPlanRepository.findByTripOrderByDayNumberAsc(persistedTrip);
        List<HotelSuggestion> hotels = hotelSuggestionRepository.findByTrip(persistedTrip);
        assertEquals(1, plans.size());
        assertEquals(1, hotels.size());
        assertTrue(("Place A".equals(plans.get(0).getPlaceName()) && "Hotel A".equals(hotels.get(0).getName()))
                || ("Place B".equals(plans.get(0).getPlaceName()) && "Hotel B".equals(hotels.get(0).getName())));
        assertTrue((21.0 == persistedTrip.getLatitude() && 81.0 == persistedTrip.getLongitude())
                || (22.0 == persistedTrip.getLatitude() && 82.0 == persistedTrip.getLongitude()));
    }

    @Test
    void externalOverpassFailureFallsBackGracefullyAndExistingTripStillLoads() throws Exception {
        when(overpassService.getTouristPlaces(anyDouble(), anyDouble(), anyInt())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("provider unavailable");
        });
        when(overpassService.getHotels(anyDouble(), anyDouble())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("provider unavailable");
        });
        when(geminiService.generateItinerary("Bengaluru", 3)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });
        when(geminiService.generateHotels("Bengaluru", 5)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });

        var generated = tripService.generateItinerary(tripId, OWNER_EMAIL);
        mockMvc.perform(get("/api/trips/{tripId}", tripId).with(user(OWNER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Bengaluru"));

        assertEquals(tripId, generated.getId());
        verifyNoInteractions(geocodingService);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
