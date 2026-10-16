package com.tripplanner.backend.security;

import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser;
        try {
            oidcUser = super.loadUser(userRequest);
        } catch (Exception e) {
            log.error("CustomOidcUserService failed in super.loadUser: exception={}, message={}", e.getClass().getName(), e.getMessage());
            throw e;
        }

        String providerId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        if (name == null || name.isBlank()) {
            name = oidcUser.getGivenName();
        }
        if (name == null || name.isBlank()) {
            name = email;
        }
        Boolean emailVerified = oidcUser.getEmailVerified();
        boolean isVerified = emailVerified != null && emailVerified;
        String picture = oidcUser.getPicture();

        try {
            User user = userService.processOAuthPostLogin("GOOGLE", providerId, email, name, isVerified, picture);
            log.info("Successfully processed OAuth post login for email={}", email);
            return new DefaultOidcUser(Collections.emptyList(), oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
        } catch (Exception e) {
            log.error("CustomOidcUserService failed in processOAuthPostLogin: exception={}, message={}", e.getClass().getName(), e.getMessage());
            throw new OAuth2AuthenticationException(e.getMessage());
        }
    }
}
