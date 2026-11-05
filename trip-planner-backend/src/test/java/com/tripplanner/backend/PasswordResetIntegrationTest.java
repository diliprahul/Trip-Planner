package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.ForgotPasswordRequest;
import com.tripplanner.backend.dto.ResetPasswordRequest;
import com.tripplanner.backend.dto.VerifyResetOtpRequest;
import com.tripplanner.backend.entity.PasswordResetToken;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.PasswordResetTokenRepository;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.repository.TripRepository;
import com.tripplanner.backend.repository.DayPlanRepository;
import com.tripplanner.backend.repository.HotelSuggestionRepository;
import com.tripplanner.backend.repository.PendingRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private DayPlanRepository dayPlanRepository;
    @Autowired private HotelSuggestionRepository hotelSuggestionRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        pendingRegistrationRepository.deleteAll();
        hotelSuggestionRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
        reset(javaMailSender);
    }

    @Test
    void existingEmailSendsAndPersistsHashedOtp() throws Exception {
        createLocalUser("user@example.com");

        requestReset(" User@Example.com ").andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(message.capture());
        assertArrayEquals(new String[]{"user@example.com"}, message.getValue().getTo());
        PasswordResetToken token = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        assertNotNull(token.getOtpHash());
        assertFalse(token.getOtpHash().matches("\\d{6}"));
        assertEquals(0, token.getAttempts());
        assertTrue(token.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(14)));
    }

    @Test
    void nonExistingEmailDoesNotSendOrPersistOtp() throws Exception {
        requestReset("missing@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No account exists with that email address."));

        verifyNoInteractions(javaMailSender);
        assertTrue(passwordResetTokenRepository.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void smtpFailureDoesNotPersistOtpOrReportSuccess() throws Exception {
        createLocalUser("user@example.com");
        doThrow(new MailSendException("SMTP unavailable"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        requestReset("user@example.com")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("We could not send the reset email. Please try again later."));

        verify(javaMailSender).send(any(SimpleMailMessage.class));
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
    }

    @Test
    void resendCooldownPreventsSecondEmailAndTokenReplacement() throws Exception {
        createLocalUser("user@example.com");
        requestReset("user@example.com").andExpect(status().isOk());
        Long firstTokenId = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow().getId();

        requestReset("user@example.com").andExpect(status().isTooManyRequests());

        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(firstTokenId, passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow().getId());
    }

    @Test
    void invalidAndExpiredOtpsAreRejected() throws Exception {
        createLocalUser("user@example.com");
        saveToken("user@example.com", "123456", LocalDateTime.now().plusMinutes(15), 0);

        verifyOtp("user@example.com", "000000").andExpect(status().isBadRequest());
        assertEquals(1, passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow().getAttempts());

        passwordResetTokenRepository.deleteAll();
        saveToken("user@example.com", "123456", LocalDateTime.now().minusMinutes(1), 0);
        verifyOtp("user@example.com", "123456").andExpect(status().isBadRequest());
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
    }

    @Test
    void maximumAttemptsInvalidatesOtp() throws Exception {
        createLocalUser("user@example.com");
        saveToken("user@example.com", "123456", LocalDateTime.now().plusMinutes(15), 0);

        for (int attempt = 0; attempt < 3; attempt++) {
            verifyOtp("user@example.com", "000000").andExpect(status().isBadRequest());
        }

        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
        verifyOtp("user@example.com", "123456").andExpect(status().isBadRequest());
    }

    @Test
    void successfulResetUpdatesPasswordAndPreventsOtpReuse() throws Exception {
        createLocalUser("user@example.com");
        saveToken("user@example.com", "123456", LocalDateTime.now().plusMinutes(15), 0);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");
        request.setNewPassword("new-secure-password1");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("new-secure-password1",
                userRepository.findByEmail("user@example.com").orElseThrow().getPassword()));
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions verifyOtp(String email, String otp) throws Exception {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return mockMvc.perform(post("/api/auth/verify-reset-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void createLocalUser(String email) {
        userRepository.save(User.builder().name("Test User").email(email)
                .password(passwordEncoder.encode("old-password")).provider("LOCAL").build());
    }

    private void saveToken(String email, String otp, LocalDateTime expiryDate, int attempts) {
        passwordResetTokenRepository.save(PasswordResetToken.builder().email(email)
                .otpHash(passwordEncoder.encode(otp)).expiryDate(expiryDate).attempts(attempts).build());
    }
}
