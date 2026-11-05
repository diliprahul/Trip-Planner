package com.tripplanner.backend;

import com.tripplanner.backend.security.AccountDeletionOAuthSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountDeletionOAuthSuccessHandlerTest {
    @Test
    void matchingGoogleIdentityCreatesOnlyTheShortLivedDeletionApproval() throws Exception {
        AccountDeletionOAuthSuccessHandler handler = handler();
        MockHttpServletRequest request = requestFor("google@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationFor("GOOGLE@example.com"));

        assertEquals("http://frontend.test/profile?googleReauthenticated=true", response.getRedirectedUrl());
        assertTrue(request.getSession().getAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_AT) instanceof Long);
    }

    @Test
    void differentGoogleIdentityInvalidatesTheSessionAndNeverApprovesDeletion() throws Exception {
        AccountDeletionOAuthSuccessHandler handler = handler();
        MockHttpServletRequest request = requestFor("google@example.com");
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationFor("other@example.com"));

        assertTrue(session.isInvalid());
        assertEquals("http://frontend.test/login?error=GoogleReauthenticationFailed", response.getRedirectedUrl());
    }

    private AccountDeletionOAuthSuccessHandler handler() {
        AccountDeletionOAuthSuccessHandler handler = new AccountDeletionOAuthSuccessHandler();
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://frontend.test");
        return handler;
    }

    private MockHttpServletRequest requestFor(String email) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_EMAIL, email);
        request.setSession(session);
        return request;
    }

    private UsernamePasswordAuthenticationToken authenticationFor(String email) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        return UsernamePasswordAuthenticationToken.authenticated(oidcUser, null, java.util.List.of());
    }
}
