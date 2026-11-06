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
import com.tripplanner.backend.service.EmailDeliveryService;
import com.tripplanner.backend.service.impl.PasswordResetPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
    private static final String GENERIC_RESET_MESSAGE = "If an account is eligible for password reset, instructions will be sent.";
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private DayPlanRepository dayPlanRepository;
    @Autowired private HotelSuggestionRepository hotelSuggestionRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @SpyBean private PasswordResetPersistenceService passwordResetPersistenceService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EmailDeliveryService emailDeliveryService;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        pendingRegistrationRepository.deleteAll();
        hotelSuggestionRepository.deleteAll();
        dayPlanRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
        reset(emailDeliveryService);
        when(emailDeliveryService.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void existingEmailSendsAndPersistsHashedOtp() throws Exception {
        createLocalUser("user@example.com");

        requestReset(" User@Example.com ").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryService).send(recipient.capture(), anyString(), anyString());
        assertEquals("user@example.com", recipient.getValue());
        PasswordResetToken token = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        assertNotNull(token.getOtpHash());
        assertFalse(token.getOtpHash().matches("\\d{6}"));
        assertEquals(0, token.getAttempts());
        assertTrue(token.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(14)));
    }

    @Test
    void emailDeliveryRunsOutsideDatabaseTransaction() throws Exception {
        createLocalUser("outside-tx@example.com");
        when(emailDeliveryService.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        requestReset("outside-tx@example.com").andExpect(status().isOk());
    }

    @Test
    void registeredAndUnregisteredEmailsReceiveIndistinguishableResponses() throws Exception {
        createLocalUser("user@example.com");
        var registeredResponse = requestReset("user@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE)).andReturn().getResponse();
        var missingResponse = requestReset("missing@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE)).andReturn().getResponse();

        assertEquals(registeredResponse.getStatus(), missingResponse.getStatus());
        assertEquals(registeredResponse.getContentAsString(), missingResponse.getContentAsString());
        verify(emailDeliveryService).send(anyString(), anyString(), anyString());
        assertTrue(passwordResetTokenRepository.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void smtpFailureReturnsSameGenericResponseAndDoesNotPersistOtp() throws Exception {
        createLocalUser("user@example.com");
        when(emailDeliveryService.send(anyString(), anyString(), anyString())).thenReturn(false);

        requestReset("user@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));

        verify(emailDeliveryService).send(anyString(), anyString(), anyString());
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
    }

    @Test
    void persistenceFailureReturnsSameGenericResponseWithoutPersistingOtp() throws Exception {
        createLocalUser("user@example.com");
        doThrow(new IllegalStateException("sensitive persistence details"))
                .when(passwordResetPersistenceService).persistToken(any(), any(), any());

        requestReset("user@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));

        verify(emailDeliveryService).send(anyString(), anyString(), anyString());
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
    }

    @Test
    void resendCooldownPreventsSecondEmailAndTokenReplacement() throws Exception {
        createLocalUser("user@example.com");
        requestReset("user@example.com").andExpect(status().isOk());
        Long firstTokenId = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow().getId();

        requestReset("user@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));

        verify(emailDeliveryService, times(1)).send(anyString(), anyString(), anyString());
        assertEquals(firstTokenId, passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow().getId());
    }

    @Test
    void resendDeliveryFailurePreservesExistingOtp() throws Exception {
        createLocalUser("user@example.com");
        requestReset("user@example.com").andExpect(status().isOk());
        PasswordResetToken original = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        original.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        passwordResetTokenRepository.save(original);
        when(emailDeliveryService.send(anyString(), anyString(), anyString())).thenReturn(false);

        requestReset("user@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));
        PasswordResetToken stillValid = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        assertEquals(original.getId(), stillValid.getId());
        assertEquals(original.getOtpHash(), stillValid.getOtpHash());
    }

    @Test
    void resendAfterCooldownReplacesOtpAndRenewsExpiry() throws Exception {
        createLocalUser("user@example.com");
        requestReset("user@example.com").andExpect(status().isOk());
        PasswordResetToken original = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        original.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        passwordResetTokenRepository.saveAndFlush(original);

        requestReset("user@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESET_MESSAGE));
        PasswordResetToken replacement = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        assertNotEquals(original.getId(), replacement.getId());
        assertNotEquals(original.getOtpHash(), replacement.getOtpHash());
        assertTrue(replacement.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(emailDeliveryService, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedReplacementTransactionRollsBackDeletionOfPreviousResetOtp() {
        createLocalUser("user@example.com");
        PasswordResetToken original = PasswordResetToken.builder().email("user@example.com")
                .otpHash(passwordEncoder.encode("123456"))
                .expiryDate(LocalDateTime.now().plusMinutes(15)).attempts(0).build();
        passwordResetTokenRepository.saveAndFlush(original);
        original.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        passwordResetTokenRepository.saveAndFlush(original);
        var state = passwordResetPersistenceService.requestState("user@example.com", LocalDateTime.now());
        assertEquals(com.tripplanner.backend.service.PasswordResetRequestResult.SENT, state.result());
        PasswordResetToken invalidReplacement = PasswordResetToken.builder().email("user@example.com")
                .otpHash("x".repeat(300)).expiryDate(LocalDateTime.now().plusMinutes(15)).attempts(0).build();

        assertThrows(DataIntegrityViolationException.class, () -> passwordResetPersistenceService.persistToken(
                invalidReplacement, state.snapshot(), LocalDateTime.now()));
        PasswordResetToken afterRollback = passwordResetTokenRepository.findByEmail("user@example.com").orElseThrow();
        assertEquals(original.getId(), afterRollback.getId());
        assertEquals(original.getOtpHash(), afterRollback.getOtpHash());
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
        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("new-secure-password1",
                userRepository.findByEmail("user@example.com").orElseThrow().getPassword()));
        assertTrue(passwordResetTokenRepository.findByEmail("user@example.com").isEmpty());
        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions verifyOtp(String email, String otp) throws Exception {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return mockMvc.perform(post("/api/auth/verify-reset-otp").with(csrf())
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
