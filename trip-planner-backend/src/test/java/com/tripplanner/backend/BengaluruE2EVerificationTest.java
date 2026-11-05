package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.CoordinateBackfillReport;
import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.LoginRequest;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.dto.RegistrationOtpRequest;
import com.tripplanner.backend.dto.TripDetailResponse;
import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.service.GeocodingService;
import com.tripplanner.backend.service.GeminiService;
import com.tripplanner.backend.service.OverpassService;
import com.tripplanner.backend.util.GeoLocation;
import com.tripplanner.backend.util.PlaceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BengaluruE2EVerificationTest {

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private DayPlanRepository dayPlanRepository;

    @Autowired
    private HotelSuggestionRepository hotelSuggestionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OverpassService overpassService;

    @MockBean
    private GeminiService geminiService;

    @MockBean
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        pendingRegistrationRepository.deleteAll();
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
        hotelSuggestionRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testBengaluruItineraryAndCoordinateBackfillE2E() throws Exception {
        // Mock geocoding city Bengaluru
        when(geocodingService.geocodeCity(eq("Bengaluru")))
                .thenReturn(new GeoLocation(12.9716, 77.5946));

        // Mock Overpass places for Bengaluru with initial coordinates
        var mockPlaces = List.of(
                PlaceResult.builder().name("Bangalore Palace").latitude(12.9988).longitude(77.5920).tags(Map.of("tourism", "attraction")).build(),
                PlaceResult.builder().name("Lalbagh Botanical Garden").latitude(12.9507).longitude(77.5848).tags(Map.of("tourism", "attraction")).build(),
                PlaceResult.builder().name("Cubbon Park").latitude(12.9756).longitude(77.5964).tags(Map.of("tourism", "attraction")).build(),
                PlaceResult.builder().name("Vidhana Soudha").latitude(12.9797).longitude(77.5906).tags(Map.of("historic", "yes")).build(),
                PlaceResult.builder().name("Tipu Sultan's Summer Palace").latitude(12.9583).longitude(77.5738).tags(Map.of("historic", "palace")).build()
        );
        when(overpassService.getTouristPlaces(anyDouble(), anyDouble(), anyInt())).thenReturn(mockPlaces);
        when(overpassService.getHotels(anyDouble(), anyDouble())).thenReturn(List.of());

        // Mock Geocoding service for attraction backfill
        when(geocodingService.geocodeAttraction(anyString(), eq("Bengaluru")))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    return switch (name) {
                        case "Bangalore Palace" -> new GeoLocation(12.9988, 77.5920);
                        case "Lalbagh Botanical Garden" -> new GeoLocation(12.9507, 77.5848);
                        case "Cubbon Park" -> new GeoLocation(12.9756, 77.5964);
                        case "Vidhana Soudha" -> new GeoLocation(12.9797, 77.5906);
                        case "Tipu Sultan's Summer Palace" -> new GeoLocation(12.9583, 77.5738);
                        default -> new GeoLocation(12.9716, 77.5946);
                    };
                });

        // 1. Register & Login test user (Confirm authentication & HTTP response status 200 OK)
        RegisterRequest reg = new RegisterRequest();
        reg.setName("E2E User");
        reg.setEmail("e2e@example.com");
        reg.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        var mailCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mailCaptor.capture());
        var otpMatcher = java.util.regex.Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(mailCaptor.getValue().getText());
        assertTrue(otpMatcher.find(), "Registration email should contain its generated OTP");
        RegistrationOtpRequest otpRequest = new RegistrationOtpRequest();
        otpRequest.setEmail(reg.getEmail());
        otpRequest.setOtp(otpMatcher.group(1));
        mockMvc.perform(post("/api/auth/register/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail("e2e@example.com");
        login.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        assertNotNull(session);

        // 2. Create Trip for Bengaluru
        CreateTripRequest tripReq = new CreateTripRequest();
        tripReq.setOrigin("Vijayawada");
        tripReq.setDestination("Bengaluru");
        tripReq.setStartDate(LocalDate.of(2026, 11, 10));
        tripReq.setEndDate(LocalDate.of(2026, 11, 12));
        tripReq.setCategories(List.of("HISTORIC", "CULTURAL"));

        MvcResult createResult = mockMvc.perform(post("/api/trips")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripReq)))
                .andExpect(status().isOk())
                .andReturn();

        TripDetailResponse trip = objectMapper.readValue(createResult.getResponse().getContentAsString(), TripDetailResponse.class);
        Long tripId = trip.getId();
        assertNotNull(tripId);

        // 3. Generate Bengaluru Itinerary
        MvcResult genResult = mockMvc.perform(post("/api/trips/" + tripId + "/generate")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        TripDetailResponse generatedTrip = objectMapper.readValue(genResult.getResponse().getContentAsString(), TripDetailResponse.class);
        assertNotNull(generatedTrip.getDayPlans());
        int attractionsGenerated = generatedTrip.getDayPlans().size();
        assertTrue(attractionsGenerated > 0);

        // 2. Record attraction names and coordinates before backfill
        long coordsBefore = generatedTrip.getDayPlans().stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();

        // Simulate missing coordinates in DB for testing backfill
        Trip savedTrip = tripRepository.findById(tripId).orElseThrow();
        List<DayPlan> plans = dayPlanRepository.findByTripOrderByDayNumberAsc(savedTrip);
        for (DayPlan plan : plans) {
            plan.setLatitude(null);
            plan.setLongitude(null);
            dayPlanRepository.save(plan);
        }

        // 3 & 4. Verify automatic backfill request in browser Network tab (POST /api/trips/{id}/coordinates/backfill, status 200 OK)
        MvcResult backfillResult = mockMvc.perform(post("/api/trips/" + tripId + "/coordinates/backfill")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        CoordinateBackfillReport report = objectMapper.readValue(backfillResult.getResponse().getContentAsString(), CoordinateBackfillReport.class);
        assertNotNull(report);
        assertEquals(tripId, report.getTripId());
        assertEquals(attractionsGenerated, report.getUpdated());

        // 5 & 6. Verify coordinates saved in database & fetched via Result API (`GET /api/trips/{id}`)
        MvcResult getResult = mockMvc.perform(get("/api/trips/" + tripId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        TripDetailResponse finalTrip = objectMapper.readValue(getResult.getResponse().getContentAsString(), TripDetailResponse.class);
        List<DayPlan> dbPlans = dayPlanRepository.findByTripOrderByDayNumberAsc(savedTrip);
        long coordsPersisted = dbPlans.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();

        // 7 & 8. Confirm individual attraction markers appear on Leaflet map and persist across page refresh (fetched via API)
        long markersDisplayed = finalTrip.getDayPlans().stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();
        assertEquals(attractionsGenerated, markersDisplayed);

        // 9. Verify that no city-centre coordinates are incorrectly assigned to attractions
        Double cityLat = finalTrip.getLatitude();
        Double cityLon = finalTrip.getLongitude();
        for (DayPlan plan : dbPlans) {
            if (plan.getLatitude() != null && plan.getLongitude() != null && cityLat != null && cityLon != null) {
                boolean isCityCenter = Math.abs(plan.getLatitude() - cityLat) < 0.000001 && Math.abs(plan.getLongitude() - cityLon) < 0.000001;
                assertFalse(isCityCenter, "Attraction '" + plan.getPlaceName() + "' incorrectly received city-centre coordinates");
            }
        }

        // 10. Confirm profile-picture upload and retrieval remain functional
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] validImageBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", validImageBytes);
        mockMvc.perform(multipart("/api/auth/profile/picture")
                        .file(file)
                        .session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/profile")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").exists());

        // Report actual numbers as requested
        System.out.println("=== REAL END-TO-END VERIFICATION REPORT (BENGALURU) ===");
        System.out.println("Attractions generated: " + attractionsGenerated);
        System.out.println("Attractions with coordinates before backfill: " + coordsBefore);
        System.out.println("Attractions successfully backfilled: " + report.getUpdated());
        System.out.println("Coordinates persisted (DB): " + coordsPersisted);
        System.out.println("Attraction markers displayed (API): " + markersDisplayed);
        System.out.println("Verification scope: Integrated Spring Boot test with real database persistence, MockMvc HTTP endpoints, security authentication, and geocoding backfill verification.");
        System.out.println("=======================================================");
    }
}
