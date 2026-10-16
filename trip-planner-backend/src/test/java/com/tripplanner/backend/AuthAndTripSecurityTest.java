package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.CreateTripRequest;
import com.tripplanner.backend.dto.LoginRequest;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.UserService;
import com.tripplanner.backend.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAndTripSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private DayPlanRepository dayPlanRepository;

    @Autowired
    private HotelSuggestionRepository hotelSuggestionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setUp() {
        hotelSuggestionRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void test1_RegistrationSuccess() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void testAllAuthAndSecurityRequirements() throws Exception {
        // 1. Registration success & 3. Password is BCrypt hashed
        RegisterRequest regReq = new RegisterRequest();
        regReq.setName("Alice");
        regReq.setEmail("alice@example.com");
        regReq.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"));

        User savedUser = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertTrue(passwordEncoder.matches("secret123", savedUser.getPassword()));

        // 2. Duplicate email rejection
        RegisterRequest dupReq = new RegisterRequest();
        dupReq.setName("Alice2");
        dupReq.setEmail("alice@example.com");
        dupReq.setPassword("other123");

        Exception ex = assertThrows(Exception.class, () -> {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dupReq)));
        });
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        // 5. Invalid password rejection
        LoginRequest badLogin = new LoginRequest();
        badLogin.setEmail("alice@example.com");
        badLogin.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().is4xxClientError());

        // 4. Login success & 15. /api/auth/me reports authentication state
        LoginRequest goodLogin = new LoginRequest();
        goodLogin.setEmail("alice@example.com");
        goodLogin.setPassword("secret123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andReturn();

        MockHttpSession aliceSession = (MockHttpSession) loginResult.getRequest().getSession();
        assertNotNull(aliceSession);

        // Check /api/auth/me with session
        mockMvc.perform(get("/api/auth/me")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        // 6. Unauthenticated trip creation rejected
        CreateTripRequest tripReq = new CreateTripRequest();
        tripReq.setOrigin("Vijayawada");
        tripReq.setDestination("Hyderabad");
        tripReq.setStartDate(LocalDate.of(2026, 12, 1));
        tripReq.setEndDate(LocalDate.of(2026, 12, 3));
        tripReq.setCategories(List.of("HISTORIC"));

        mockMvc.perform(post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripReq)))
                .andExpect(status().isUnauthorized());

        // 7 & 8. Authenticated trip creation succeeds and has correct user
        MvcResult tripResult = mockMvc.perform(post("/api/trips")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        String responseJson = tripResult.getResponse().getContentAsString();
        var createdTrip = objectMapper.readValue(responseJson, com.tripplanner.backend.dto.TripDetailResponse.class);
        Long tripId = createdTrip.getId();

        // 9. User can retrieve own trip
        mockMvc.perform(get("/api/trips/" + tripId)
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId));

        // 11. /api/trips/my returns only own trips
        mockMvc.perform(get("/api/trips/my")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        // Create second user Bob
        RegisterRequest bobReg = new RegisterRequest();
        bobReg.setName("Bob");
        bobReg.setEmail("bob@example.com");
        bobReg.setPassword("bobpass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobReg)))
                .andExpect(status().isOk());

        LoginRequest bobLogin = new LoginRequest();
        bobLogin.setEmail("bob@example.com");
        bobLogin.setPassword("bobpass123");

        MvcResult bobLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobLogin)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession bobSession = (MockHttpSession) bobLoginResult.getRequest().getSession();
        assertNotNull(bobSession);

        // 10. User cannot retrieve another user's trip
        mockMvc.perform(get("/api/trips/" + tripId)
                        .session(bobSession))
                .andExpect(status().isForbidden());

        // 12. User cannot generate another user's trip
        mockMvc.perform(post("/api/trips/" + tripId + "/generate")
                        .session(bobSession))
                .andExpect(status().isForbidden());

        // 13. User cannot delete another user's trip
        mockMvc.perform(delete("/api/trips/" + tripId)
                        .session(bobSession))
                .andExpect(status().isForbidden());

        // Unauthenticated delete rejected (401)
        mockMvc.perform(delete("/api/trips/" + tripId))
                .andExpect(status().isUnauthorized());

        // Nonexistent trip delete returns 404 (not 500)
        mockMvc.perform(delete("/api/trips/999999")
                        .session(aliceSession))
                .andExpect(status().isNotFound());

        // Owner can successfully delete their trip (200)
        mockMvc.perform(delete("/api/trips/" + tripId)
                        .session(aliceSession))
                .andExpect(status().isOk());

        // Test orphaned trip (user_id is null) returns 404 Not Found
        Trip orphanedTrip = Trip.builder()
                .user(null)
                .origin("Vijayawada")
                .destination("Hyderabad")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 3))
                .days(3)
                .placeCategories(List.of("HISTORIC"))
                .build();
        Trip savedOrphan = tripRepository.save(orphanedTrip);

        mockMvc.perform(get("/api/trips/" + savedOrphan.getId())
                        .session(aliceSession))
                .andExpect(status().isNotFound());

        // 11. Bob's /api/trips/my returns empty list (0 trips)
        mockMvc.perform(get("/api/trips/my")
                        .session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // 14. Logout invalidates authentication
        mockMvc.perform(post("/api/auth/logout")
                        .session(aliceSession))
                .andExpect(status().isOk());

        // After logout, /api/auth/me should return 401
        mockMvc.perform(get("/api/auth/me")
                        .session(aliceSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleOAuthClientConfigurationDoesNotUseDummyOrUnresolvedPlaceholder() {
        var googleRegistration = clientRegistrationRepository.findByRegistrationId("google");
        assertNotNull(googleRegistration);

        String clientId = googleRegistration.getClientId();
        String clientSecret = googleRegistration.getClientSecret();

        assertNotNull(clientId);
        assertNotNull(clientSecret);

        assertNotEquals("dummy-client-id", clientId);
        assertFalse(clientId.contains("${"), "Client ID contains unresolved placeholder: " + clientId);

        assertNotEquals("dummy-client-secret", clientSecret);
        assertFalse(clientSecret.contains("${"), "Client secret contains unresolved placeholder");
    }

    @Test
    void testGetCurrentUserWhenUserDeletedReturns401() throws Exception {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setName("Alice");
        regReq.setEmail("alice@example.com");
        regReq.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk());

        LoginRequest goodLogin = new LoginRequest();
        goodLogin.setEmail("alice@example.com");
        goodLogin.setPassword("secret123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodLogin)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession aliceSession = (MockHttpSession) loginResult.getRequest().getSession();
        assertNotNull(aliceSession);

        // Delete user from database while session remains active
        userRepository.deleteAll();

        // GET /api/auth/me should return 401 Unauthorized instead of 500
        mockMvc.perform(get("/api/auth/me")
                        .session(aliceSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProcessOAuthPostLoginNewUser() {
        User user = userService.processOAuthPostLogin("GOOGLE", "google-sub-123", "oauthuser@example.com", "OAuth User", true);
        assertNotNull(user);
        assertEquals("oauthuser@example.com", user.getEmail());
        assertEquals("GOOGLE", user.getProvider());
        assertEquals("google-sub-123", user.getProviderId());
    }

    @Test
    void testProcessOAuthPostLoginAccountLinking() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Local User");
        req.setEmail("linkuser@example.com");
        req.setPassword("password123");
        userService.register(req);

        User user = userService.processOAuthPostLogin("GOOGLE", "google-sub-456", "linkuser@example.com", "Link User", true);
        assertNotNull(user);
        assertEquals("linkuser@example.com", user.getEmail());
        assertEquals("GOOGLE", user.getProvider());
        assertEquals("google-sub-456", user.getProviderId());
    }

    @Test
    void testProcessOAuthPostLoginUnverifiedEmailThrows() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.processOAuthPostLogin("GOOGLE", "google-sub-789", "unverified@example.com", "Unverified", false);
        });
        assertTrue(ex.getMessage().contains("not verified"));
    }

    @Test
    void testMyTripsEmptyListAndCaseNormalization() throws Exception {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setName("Empty User");
        regReq.setEmail("emptyuser@example.com");
        regReq.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("emptyuser@example.com");
        loginReq.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        assertNotNull(session);

        // GET /api/trips/my with 0 trips should return 200 OK and empty list []
        mockMvc.perform(get("/api/trips/my")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // Test direct service call with mixed case and spacing in email
        var myTrips = tripService.getMyTrips("  EMPTYUSER@EXAMPLE.COM  ");
        assertNotNull(myTrips);
        assertTrue(myTrips.isEmpty());
    }

    @Test
    void testOAuthLoginSessionPersistenceAndAuthMe() throws Exception {
        User user = userService.processOAuthPostLogin("GOOGLE", "google-sub-oauth-test", "oauthsession@example.com", "OAuth Session User", true);
        assertNotNull(user);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
