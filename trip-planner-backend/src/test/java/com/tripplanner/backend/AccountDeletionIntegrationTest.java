package com.tripplanner.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.backend.dto.LoginRequest;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.security.AccountDeletionOAuthSuccessHandler;
import com.tripplanner.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void incorrectLocalDeletionPasswordReturnsStructured401AndKeepsAccount() throws Exception {
        MockHttpSession session = loginLocal("local@example.com", "correct123");

        mockMvc.perform(delete("/api/auth/account").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\",\"confirmed\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Incorrect password. Please enter your correct password."));

        assertTrue(userRepository.existsByEmail("local@example.com"));
        assertFalse(session.isInvalid());
    }

    @Test
    void localDeletionInvalidatesOnlyDeletedUsersSession() throws Exception {
        MockHttpSession aliceSession = loginLocal("alice@example.com", "correct123");
        loginLocal("bob@example.com", "another123");

        mockMvc.perform(delete("/api/auth/account").session(aliceSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"correct123\",\"confirmed\":true}"))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsByEmail("alice@example.com"));
        assertTrue(userRepository.existsByEmail("bob@example.com"));
        assertTrue(aliceSession.isInvalid());
    }

    @Test
    void googleDeletionRequiresFreshMatchingReauthenticationThenDeletes() throws Exception {
        User google = userService.processOAuthPostLogin("GOOGLE", "subject-1", "google@example.com", "Google", true);
        MockHttpSession session = loginAsExistingGoogleUser(google.getEmail());

        mockMvc.perform(delete("/api/auth/account").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_REAUTHENTICATION_REQUIRED"));
        assertTrue(userRepository.existsByEmail("google@example.com"));

        session.setAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_EMAIL, "google@example.com");
        session.setAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_AT, Instant.now().toEpochMilli());
        mockMvc.perform(delete("/api/auth/account").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isNoContent());
        assertFalse(userRepository.existsByEmail("google@example.com"));
        assertTrue(session.isInvalid());
    }

    private MockHttpSession loginLocal(String email, String password) throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setName(email); register.setEmail(email); register.setPassword(password);
        userService.register(register);
        return login(email, password);
    }

    private MockHttpSession loginAsExistingGoogleUser(String email) throws Exception {
        // A session principal is the server-side account identity; the OAuth callback itself is covered by configuration.
        return login(email, null);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        if (password == null) {
            var principal = org.springframework.security.core.userdetails.User.withUsername(email).password("N/A").authorities("ROLE_USER").build();
            MockHttpSession session = new MockHttpSession();
            var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);
            return session;
        }
        LoginRequest request = new LoginRequest(); request.setEmail(email); request.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }
}
