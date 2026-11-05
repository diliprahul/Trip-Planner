package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.dto.RegistrationOtpRequest;
import com.tripplanner.backend.entity.PendingRegistration;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.service.UserService;
import com.tripplanner.backend.service.impl.PendingRegistrationCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationVerificationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private DayPlanRepository dayPlanRepository;
    @Autowired private HotelSuggestionRepository hotelSuggestionRepository;
    @Autowired private PendingRegistrationRepository pendingRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserService userService;
    @Autowired private PendingRegistrationCleanupService pendingRegistrationCleanupService;
    @MockBean private JavaMailSender javaMailSender;

    @BeforeEach void resetData() {
        pendingRepository.deleteAll();
        hotelSuggestionRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
        reset(javaMailSender);
    }

    @Test void requestNormalizesEmailSendsOtpAndCreatesNoUser() throws Exception {
        register(" Alice@Example.COM ").andExpect(status().isOk());
        verify(javaMailSender).send(any(SimpleMailMessage.class));
        assertTrue(userRepository.findByEmail("alice@example.com").isEmpty());
        PendingRegistration pending = pendingRepository.findByEmail("alice@example.com").orElseThrow();
        assertFalse(pending.getOtpHash().matches("\\d{6}"));
        assertTrue(pending.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(4)));
        assertTrue(pending.getExpiryDate().isBefore(LocalDateTime.now().plusMinutes(6)));
    }

    @Test void cleanupDeletesExpiredPendingRegistrationsOnly() {
        LocalDateTime now = LocalDateTime.now();
        savePending("expired@example.com", "123456", now.minusSeconds(1), 0);
        savePending("boundary@example.com", "123456", now, 0);
        savePending("active@example.com", "123456", now.plusMinutes(5), 0);

        pendingRegistrationCleanupService.deleteExpiredRegistrations();

        assertTrue(pendingRepository.findByEmail("expired@example.com").isEmpty());
        assertTrue(pendingRepository.findByEmail("boundary@example.com").isEmpty());
        assertTrue(pendingRepository.findByEmail("active@example.com").isPresent());
    }

    @Test void rejectsInvalidAndDuplicateRegisteredEmails() throws Exception {
        register("not-an-email").andExpect(status().isBadRequest());
        userRepository.save(User.builder().name("Existing").email("existing@example.com").password("hash").build());
        register("existing@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email already exists."));
        verifyNoInteractions(javaMailSender);
    }

    @Test void duplicatePendingAndCooldownAreRejected() throws Exception {
        register("user@example.com").andExpect(status().isOk());
        register("user@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email already exists."));
        resend("user@example.com").andExpect(status().isTooManyRequests());
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void expiredPendingRegistrationCanBeReplacedButValidOneCannot() throws Exception {
        savePending("expired@example.com", "123456", LocalDateTime.now().minusSeconds(1), 0);
        register("expired@example.com").andExpect(status().isOk());
        PendingRegistration replaced = pendingRepository.findByEmail("expired@example.com").orElseThrow();
        assertTrue(replaced.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(4)));
        assertEquals(1, pendingRepository.count());

        savePending("valid@example.com", "123456", LocalDateTime.now().plusMinutes(2), 0);
        register("valid@example.com").andExpect(status().isConflict());
        assertEquals(2, pendingRepository.count());
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void resendExtendsPendingOtpByFiveMinutes() throws Exception {
        savePending("user@example.com", "123456", LocalDateTime.now().plusMinutes(1), 0);
        resend("user@example.com").andExpect(status().isOk());
        PendingRegistration pending = pendingRepository.findByEmail("user@example.com").orElseThrow();
        assertTrue(pending.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(4)));
        assertTrue(pending.getExpiryDate().isBefore(LocalDateTime.now().plusMinutes(6)));
    }

    @Test void smtpFailureDoesNotCreatePendingRegistration() throws Exception {
        doThrow(new MailSendException("unavailable")).when(javaMailSender).send(any(SimpleMailMessage.class));
        register("user@example.com").andExpect(status().isServiceUnavailable());
        assertTrue(pendingRepository.findByEmail("user@example.com").isEmpty());
    }

    @Test void invalidExpiredAndMaximumAttemptOtpsDoNotCreateAccounts() throws Exception {
        savePending("user@example.com", "123456", LocalDateTime.now().plusMinutes(5), 0);
        verifyOtp("user@example.com", "000000").andExpect(status().isBadRequest());
        assertTrue(userRepository.findByEmail("user@example.com").isEmpty());
        for (int i = 1; i < 5; i++) verifyOtp("user@example.com", "000000").andExpect(status().isBadRequest());
        assertTrue(pendingRepository.findByEmail("user@example.com").isEmpty());

        savePending("expired@example.com", "123456", LocalDateTime.now().minusMinutes(1), 0);
        verifyOtp("expired@example.com", "123456").andExpect(status().isBadRequest());
        assertTrue(userRepository.findByEmail("expired@example.com").isEmpty());
    }

    @Test void correctOtpCreatesEncodedUserSessionAndCannotBeReused() throws Exception {
        savePending("user@example.com", "123456", LocalDateTime.now().plusMinutes(5), 0);
        MvcResult result = verifyOtp("user@example.com", "123456").andExpect(status().isOk()).andReturn();
        User user = userRepository.findByEmail("user@example.com").orElseThrow();
        assertTrue(passwordEncoder.matches("password123", user.getPassword()));
        assertTrue(pendingRepository.findByEmail("user@example.com").isEmpty());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        verifyOtp("user@example.com", "123456").andExpect(status().isBadRequest());
    }

    @Test void googleVerifiedEmailStillCreatesOAuthAccountWithoutRegistrationOtp() {
        User user = userService.processOAuthPostLogin("GOOGLE", "sub-1", "google@example.com", "Google User", true);
        assertEquals("GOOGLE", user.getProvider());
        assertTrue(pendingRepository.findByEmail("google@example.com").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(); request.setName("Test User"); request.setEmail(email); request.setPassword("password123");
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }
    private org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
        RegistrationOtpRequest request = new RegistrationOtpRequest(); request.setEmail(email);
        return mockMvc.perform(post("/api/auth/register/resend-otp").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }
    private org.springframework.test.web.servlet.ResultActions verifyOtp(String email, String otp) throws Exception {
        RegistrationOtpRequest request = new RegistrationOtpRequest(); request.setEmail(email); request.setOtp(otp);
        return mockMvc.perform(post("/api/auth/register/verify-otp").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }
    private void savePending(String email, String otp, LocalDateTime expiry, int attempts) {
        pendingRepository.save(PendingRegistration.builder().email(email).name("Test User").passwordHash(passwordEncoder.encode("password123"))
                .otpHash(passwordEncoder.encode(otp)).expiryDate(expiry).attempts(attempts).lastSentAt(LocalDateTime.now().minusMinutes(2)).build());
    }
}
