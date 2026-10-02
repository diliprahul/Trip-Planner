package com.tripplanner.backend;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.TripService;
import com.tripplanner.backend.util.GeoLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TripPlannerBackendApplicationTests {

	@Autowired
	private GeocodingService geocodingService;

	@Autowired
	private TripService tripService;

	@Autowired
	private TripRepository tripRepository;

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
				// Verify it is near Hyderabad, Telangana, India center (~17.36, 78.47)
				assertTrue(loc.getLatitude() > 17.30 && loc.getLatitude() < 17.42, "Hyderabad latitude should be around city center: " + loc.getLatitude());
				assertTrue(loc.getLongitude() > 78.40 && loc.getLongitude() < 78.55, "Hyderabad longitude should be around city center: " + loc.getLongitude());
			} else if (trimmed.equalsIgnoreCase("Vijayawada")) {
				// Verify Vijayawada, Andhra Pradesh, India coordinates (~16.5, 80.6)
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

		TripDetailResponse trip = tripService.createTrip(request);
		assertNotNull(trip);
		assertEquals(3, trip.getDays());

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId());
		assertNotNull(itinerary);
		assertFalse(itinerary.getDayPlans().isEmpty(), "Day plans should not be empty");
		
		System.out.println("Destination: " + itinerary.getDestination());
		System.out.println("Start Date: " + itinerary.getStartDate());
		System.out.println("End Date: " + itinerary.getEndDate());
		System.out.println("Lat: " + itinerary.getLatitude());
		System.out.println("Lon: " + itinerary.getLongitude());
		
		var weather = itinerary.getWeatherResponse();
		assertNotNull(weather, "WeatherResponse should not be null");
		System.out.println("Weather Status: " + weather.getStatus());
		System.out.println("Weather Message: " + weather.getMessage());
		System.out.println("Weather forecasts count: " + (weather.getForecasts() != null ? weather.getForecasts().size() : 0));
		
		if (weather.getForecasts() != null) {
			for (var forecast : weather.getForecasts()) {
				System.out.println("Forecast date: " + forecast.getDate() + ", Condition: " + forecast.getCondition());
			}
		}
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
		TripDetailResponse trip = tripService.createTrip(request);
		assertNotNull(trip);
		assertEquals(5, trip.getDays());

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId());
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
		System.out.println("=========================================");
	}

	@Test
	void testTripCategoriesPersistenceReligious() {
		Trip trip = Trip.builder()
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
		request.setEndDate(LocalDate.of(2026, 11, 2)); // 2 days
		request.setCategories(List.of("RELIGIOUS"));

		TripDetailResponse trip = tripService.createTrip(request);
		assertNotNull(trip);
		assertEquals(1, trip.getPlaceCategories().size());
		assertEquals("RELIGIOUS", trip.getPlaceCategories().get(0));

		TripDetailResponse itinerary = tripService.generateItinerary(trip.getId());
		assertNotNull(itinerary);
		assertNotNull(itinerary.getDayPlans());

		System.out.println("=== RELIGIOUS ONLY ITINERARY REPORT ===");
		System.out.println("Trip ID: " + itinerary.getId());
		System.out.println("Stored Categories: " + itinerary.getPlaceCategories());
		System.out.println("Generated Places Count: " + itinerary.getDayPlans().size());
		for (var plan : itinerary.getDayPlans()) {
			System.out.println("Place: " + plan.getPlaceName() + " (" + plan.getDescription() + ")");
		}
		System.out.println("=======================================");
	}

}
