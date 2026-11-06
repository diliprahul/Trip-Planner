package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.LoginRequest;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.EmailDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CsrfProtectionIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private DayPlanRepository dayPlanRepository;
    @Autowired private HotelSuggestionRepository hotelRepository;
    @Autowired private PendingRegistrationRepository pendingRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailDeliveryService emailDeliveryService;

    @BeforeEach
    void cleanData() {
        resetTokenRepository.deleteAll();
        pendingRepository.deleteAll();
        hotelRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
        when(emailDeliveryService.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void unsafeRequestsRejectMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        CsrfSession csrf = bootstrap();
        mockMvc.perform(post("/api/auth/register").session(csrf.session())
                        .header("X-CSRF-TOKEN", "not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapTokenAllowsRegistrationAndCredentialedCrossOriginPreflight() throws Exception {
        CsrfSession csrf = bootstrap();
        mockMvc.perform(post("/api/auth/register").session(csrf.session())
                        .header(csrf.headerName(), csrf.token())
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CSRF User\",\"email\":\"csrf@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void loginAndLogoutRequireAndRefreshSessionCsrfToken() throws Exception {
        userRepository.save(User.builder().name("Login User").email("login@example.com")
                .password(passwordEncoder.encode("password123")).provider("LOCAL").build());
        CsrfSession initial = bootstrap();

        LoginRequest login = new LoginRequest();
        login.setEmail("login@example.com");
        login.setPassword("password123");
        String loginJson = objectMapper.writeValueAsString(login);

        mockMvc.perform(post("/api/auth/login").session(initial.session())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isForbidden());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").session(initial.session())
                        .header(initial.headerName(), initial.token())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(authenticatedSession);

        CsrfSession refreshed = bootstrap(authenticatedSession);
        mockMvc.perform(put("/api/auth/profile").session(refreshed.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\",\"cityOfResidence\":\"Pune\",\"favouritePlace\":\"Pune\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/auth/profile").session(refreshed.session())
                        .header(refreshed.headerName(), refreshed.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\",\"cityOfResidence\":\"Pune\",\"favouritePlace\":\"Pune\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/trips").session(refreshed.session())
                        .header(refreshed.headerName(), refreshed.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"Pune\",\"destination\":\"Mumbai\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-02\",\"categories\":[\"CULTURAL\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").session(refreshed.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").session(refreshed.session())
                        .header(refreshed.headerName(), refreshed.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(refreshed.session()))
                .andExpect(status().isUnauthorized());
    }

    private CsrfSession bootstrap() throws Exception {
        return bootstrap(null);
    }

    private CsrfSession bootstrap(MockHttpSession existingSession) throws Exception {
        var request = get("/api/auth/csrf");
        if (existingSession != null) request.session(existingSession);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfSession(session, json.get("headerName").asText(), json.get("token").asText());
    }

    private record CsrfSession(MockHttpSession session, String headerName, String token) {}
}
