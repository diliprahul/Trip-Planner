package com.tripplanner.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AccountDeletionOAuthSuccessHandler implements AuthenticationSuccessHandler {
    public static final String DELETE_REAUTH_EMAIL = "DELETE_REAUTH_EMAIL";
    public static final String DELETE_REAUTH_AT = "DELETE_REAUTH_AT";

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        String deletionEmail = session == null ? null : (String) session.getAttribute(DELETE_REAUTH_EMAIL);
        if (deletionEmail == null) {
            response.sendRedirect(frontendUrl + "/");
            return;
        }

        String googleEmail = authentication.getPrincipal() instanceof OidcUser oidcUser ? oidcUser.getEmail() : null;
        if (googleEmail == null || !deletionEmail.equalsIgnoreCase(googleEmail.trim())) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(frontendUrl + "/login?error=GoogleReauthenticationFailed");
            return;
        }

        session.setAttribute(DELETE_REAUTH_AT, Instant.now().toEpochMilli());
        response.sendRedirect(frontendUrl + "/profile?googleReauthenticated=true");
    }
}
