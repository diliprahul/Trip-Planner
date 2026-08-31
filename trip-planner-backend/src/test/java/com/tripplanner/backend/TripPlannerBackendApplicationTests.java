package com.tripplanner.backend;

import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
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

/*
	@Autowired
	private GeocodingService geocodingService;
*/
	@Autowired
	private TripService tripService;

	@Test
	void contextLoads() {
	}

/*
	@Test
	void testGeocodingService() {
		GeoLocation loc = geocodingService.geocodeCity("Hyderabad");
		assertNotNull(loc);
		assertNotEquals(0.0, loc.getLatitude());
		assertNotEquals(0.0, loc.getLongitude());
		System.out.println("Geocoded Hyderabad: Lat=" + loc.getLatitude() + ", Lon=" + loc.getLongitude());
	}
*/

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

}
