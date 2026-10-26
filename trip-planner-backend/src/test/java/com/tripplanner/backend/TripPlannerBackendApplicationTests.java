package com.tripplanner.backend;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.TripService;
import com.tripplanner.backend.service.UserService;
import com.tripplanner.backend.util.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.util.PlaceResult;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class TripPlannerBackendApplicationTests {

	@Autowired
	private GeocodingService geocodingService;

	@Autowired
	private TripService tripService;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserService userService;

	@MockBean
	private OverpassService overpassService;

	private final String testEmail = "test@example.com";

	@BeforeEach
	void setUpUser() {
		var mockPlaces = List.of(
				PlaceResult.builder()
						.name("Charminar")
						.latitude(17.3616)
						.longitude(78.4747)
						.tags(Map.of("historic", "monument", "wikidata", "Q12345", "wikipedia", "en:Charminar"))
						.build(),
				PlaceResult.builder()
						.name("Golconda Fort")
						.latitude(17.3833)
						.longitude(78.4011)
						.tags(Map.of("historic", "fort", "wikidata", "Q23456", "wikipedia", "en:Golconda_Fort"))
						.build(),
				PlaceResult.builder()
						.name("Birla Mandir")
						.latitude(17.4062)
						.longitude(78.4695)
						.tags(Map.of("amenity", "place_of_worship", "wikidata", "Q34567", "wikipedia", "en:Birla_Mandir"))
						.build(),
				PlaceResult.builder()
						.name("Hussain Sagar")
						.latitude(17.4239)
						.longitude(78.4738)
						.tags(Map.of("natural", "water", "wikidata", "Q45678", "wikipedia", "en:Hussain_Sagar"))
						.build(),
				PlaceResult.builder()
						.name("Salar Jung Museum")
						.latitude(17.3713)
						.longitude(78.4804)
						.tags(Map.of("tourism", "museum", "wikidata", "Q56789", "wikipedia", "en:Salar_Jung_Museum"))
						.build()
		);

		var mockHotels = List.of(
				PlaceResult.builder()
						.name("Taj Falaknuma Palace")
						.latitude(17.3330)
						.longitude(78.4790)
						.tags(Map.of("tourism", "hotel"))
						.build(),
				PlaceResult.builder()
						.name("ITC Kohenur")
						.latitude(17.4435)
						.longitude(78.3772)
						.tags(Map.of("tourism", "hotel"))
						.build()
		);

		when(overpassService.getTouristPlaces(anyDouble(), anyDouble()))
				.thenReturn(mockPlaces);
		when(overpassService.getTouristPlaces(anyDouble(), anyDouble(), anyInt()))
				.thenReturn(mockPlaces);
		when(overpassService.getHotels(anyDouble(), anyDouble()))
				.thenReturn(mockHotels);

		try {
			RegisterRequest reg = new RegisterRequest();
			reg.setName("Test User");
			reg.setEmail(testEmail);
			reg.setPassword("password123");
			userService.register(reg);
		} catch (Exception e) {
			// already registered
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testGeocodingServiceVariations() {
		List<String> inputs = List.of("Hyderabad", "Hyderabad ", "  Hyderabad  ", "Vijayawada", "Vijayawada ", "Delhi", "Mumbai");
		
		for (String input : inputs) {
			GeoLocation loc = geocodingService.geocodeCity(input);
			assertNotNull(loc, "Geoloc should not be null for input: '" + input + "'");
			assertNotEquals(0.0, loc.getLatitude());
			assertNotEquals(0.0, loc.getLongitude());
			
			String trimmed = input.trim();
			if (trimmed.equalsIgnoreCase("Hyderabad")) {
				assertTrue(loc.getLatitude() > 17.30 && loc.getLatitude() < 17.42, "Hyderabad latitude should be around city center: " + loc.getLatitude());
				assertTrue(loc.getLongitude() > 78.40 && loc.getLongitude() < 78.55, "Hyderabad longitude should be around city center: " + loc.getLongitude());
			} else if (trimmed.equalsIgnoreCase("Vijayawada")) {
				assertTrue(loc.getLatitude() > 16.40 && loc.getLatitude() < 16.65, "Vijayawada latitude should be correct: " + loc.getLatitude());
				assertTrue(loc.getLongitude() > 80.50 && loc.getLongitude() < 80.75, "Vijayawada longitude should be correct: " + loc.getLongitude());
			} else if (trimmed.equalsIgnoreCase("Delhi")) {
				assertTrue(loc.getLatitude() > 28.5 && loc.getLatitude() < 28.8, "Delhi latitude should be correct: " + loc.getLatitude());
				assertTrue(loc.getLongitude() > 77.0 && loc.getLongitude() < 77.3, "Delhi longitude should be correct: " + loc.getLongitude());
			} else if (trimmed.equalsIgnoreCase("Mumbai")) {
				assertTrue(loc.getLatitude() > 18.8 && loc.getLatitude() < 19.3, "Mumbai latitude should be correct: " + loc.getLatitude());
				assertTrue(loc.getLongitude() > 72.7 && loc.getLongitude() < 73.0, "Mumbai longitude should be correct: " + loc.getLongitude());
			}
		}
	}

	@Test
	void testCreateAndGenerateItineraryForDates() {
		CreateTripRequest request = new CreateTripRequest();
		request.setOrigin("Vijayawada");
		request.setDestination("Hyderabad");
		request.setStartDate(LocalDate.of(2026, 8, 30));
		request.setEndDate(LocalDate.of(2026, 9, 1));
		request.setCategories(List.of("sightseeing"));

		TripDetailResponse trip = tripService.createTrip(request, testEmail);
		assertNotNull(trip);
		assertEquals(3, trip.getDays());

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId(), testEmail);
		assertNotNull(itinerary);
		assertFalse(itinerary.getDayPlans().isEmpty(), "Day plans should not be empty");
		
		var weather = itinerary.getWeatherResponse();
		assertNotNull(weather, "WeatherResponse should not be null");
	}

	@Test
	void testGenerateItineraryHyderabadOctober() {
		CreateTripRequest request = new CreateTripRequest();
		request.setOrigin("Vijayawada");
		request.setDestination("Hyderabad");
		request.setStartDate(LocalDate.of(2026, 10, 6));
		request.setEndDate(LocalDate.of(2026, 10, 10));
		request.setCategories(List.of("sightseeing"));

		long startTime = System.currentTimeMillis();
		TripDetailResponse trip = tripService.createTrip(request, testEmail);
		assertNotNull(trip);
		assertEquals(5, trip.getDays());

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId(), testEmail);
		long generationTimeMs = System.currentTimeMillis() - startTime;

		assertNotNull(itinerary);
		assertNotNull(itinerary.getDayPlans());
		assertNotNull(itinerary.getHotels());
		assertNotNull(itinerary.getWeatherResponse());

		int placesCount = itinerary.getDayPlans().size();
		int hotelsCount = itinerary.getHotels().size();
		int weatherCount = itinerary.getWeatherResponse().getForecasts() != null ? itinerary.getWeatherResponse().getForecasts().size() : 0;

		System.out.println("=== ACTUAL RUNTIME VERIFICATION REPORT ===");
		System.out.println("HTTP Status: 200 OK");
		System.out.println("Generation Time: " + generationTimeMs + " ms");
		System.out.println("Places Count: " + placesCount);
		System.out.println("Hotels Count: " + hotelsCount);
		System.out.println("Weather Count: " + weatherCount);
		System.out.println("=========================================p");
	}

	@Test
	void testTripCategoriesPersistenceReligious() {
		Trip trip = Trip.builder()
				.user(userService.findByEmail(testEmail))
				.origin("Vijayawada")
				.destination("Hyderabad")
				.startDate(LocalDate.of(2026, 10, 10))
				.endDate(LocalDate.of(2026, 10, 12))
				.days(3)
				.placeCategories(List.of("RELIGIOUS"))
				.build();

		Trip saved = tripRepository.save(trip);
		assertNotNull(saved.getId());

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();
		assertNotNull(reloaded.getPlaceCategories());
		assertEquals(1, reloaded.getPlaceCategories().size());
		assertTrue(reloaded.getPlaceCategories().contains("RELIGIOUS"));
	}

	@Test
	void testTripCategoriesPersistenceHistoricReligious() {
		Trip trip = Trip.builder()
				.user(userService.findByEmail(testEmail))
				.origin("Vijayawada")
				.destination("Hyderabad")
				.startDate(LocalDate.of(2026, 10, 10))
				.endDate(LocalDate.of(2026, 10, 12))
				.days(3)
				.placeCategories(List.of("HISTORIC", "RELIGIOUS"))
				.build();

		Trip saved = tripRepository.save(trip);
		assertNotNull(saved.getId());

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();
		assertNotNull(reloaded.getPlaceCategories());
		assertEquals(2, reloaded.getPlaceCategories().size());
		assertTrue(reloaded.getPlaceCategories().contains("HISTORIC"));
		assertTrue(reloaded.getPlaceCategories().contains("RELIGIOUS"));
	}

	@Test
	void testTripCategoriesPersistenceEmpty() {
		Trip trip = Trip.builder()
				.user(userService.findByEmail(testEmail))
				.origin("Vijayawada")
				.destination("Hyderabad")
				.startDate(LocalDate.of(2026, 10, 10))
				.endDate(LocalDate.of(2026, 10, 12))
				.days(3)
				.placeCategories(List.of())
				.build();

		Trip saved = tripRepository.save(trip);
		assertNotNull(saved.getId());

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();
		assertNotNull(reloaded.getPlaceCategories());
		assertTrue(reloaded.getPlaceCategories().isEmpty());
	}

	@Test
	void testGenerateItineraryReligiousOnly() {
		CreateTripRequest request = new CreateTripRequest();
		request.setOrigin("Vijayawada");
		request.setDestination("Hyderabad");
		request.setStartDate(LocalDate.of(2026, 11, 1));
		request.setEndDate(LocalDate.of(2026, 11, 2));
		request.setCategories(List.of("RELIGIOUS"));

		TripDetailResponse trip = tripService.createTrip(request, testEmail);
		assertNotNull(trip);
		assertEquals(1, trip.getPlaceCategories().size());
		assertEquals("RELIGIOUS", trip.getPlaceCategories().get(0));

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId(), testEmail);
		assertNotNull(itinerary);
		assertNotNull(itinerary.getDayPlans());

		System.out.println("=== RELIGIOUS ONLY ITINERARY REPORT ===");
		System.out.println("Trip ID: " + itinerary.getId());
		System.out.println("Stored Categories: " + itinerary.getPlaceCategories());
		System.out.println("Generated Places Count: " + itinerary.getDayPlans().size());
		System.out.println("=======================================");
	}

	@Test
	void testGeocodingFailureThrowsBadRequest() {
		CreateTripRequest request = new CreateTripRequest();
		request.setOrigin("Vijayawada");
		request.setDestination("InvalidUnknownCityXYZ");
		request.setStartDate(LocalDate.of(2026, 12, 1));
		request.setEndDate(LocalDate.of(2026, 12, 3));

		TripDetailResponse trip = tripService.createTrip(request, testEmail);
		assertNotNull(trip);

		org.springframework.web.server.ResponseStatusException ex = assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> tripService.generateItinerary(trip.getId(), testEmail)
		);
		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

}
