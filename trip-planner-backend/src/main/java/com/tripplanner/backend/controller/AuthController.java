package com.tripplanner.backend.controller;
import org.springframework.security.core.context.SecurityContext;
import com.tripplanner.backend.dto.*;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.service.PasswordResetService;
import com.tripplanner.backend.service.PasswordResetRequestResult;
import com.tripplanner.backend.service.UserService;
import com.tripplanner.backend.service.RegistrationVerificationService;
import com.tripplanner.backend.service.RegistrationVerificationResult;
import com.tripplanner.backend.service.IncorrectPasswordException;
import com.tripplanner.backend.security.AccountDeletionOAuthSuccessHandler;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordResetService passwordResetService;
    private final RegistrationVerificationService registrationVerificationService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        try {
            RegistrationVerificationResult result = registrationVerificationService.requestRegistration(request);
            return switch (result) {
                case SENT -> ResponseEntity.ok(Map.of("message", "Verification code sent. Check your email."));
                case EMAIL_REGISTERED, PENDING_EXISTS -> ResponseEntity.status(409).body(existingEmailResponse());
                case COOLDOWN_ACTIVE -> ResponseEntity.status(429).body(Map.of("message", "Please wait one minute before requesting another code."));
                case DELIVERY_FAILED -> ResponseEntity.status(503).body(Map.of("message", "We could not send the verification email. Please try again later."));
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(existingEmailResponse());
        }
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<Map<String, String>> resendRegistrationOtp(@RequestBody RegistrationOtpRequest request) {
        try {
            RegistrationVerificationResult result = registrationVerificationService.resendOtp(request.getEmail());
            return switch (result) {
                case SENT -> ResponseEntity.ok(Map.of("message", "Verification code sent. Check your email."));
                case COOLDOWN_ACTIVE -> ResponseEntity.status(429).body(Map.of("message", "Please wait one minute before requesting another code."));
                case DELIVERY_FAILED -> ResponseEntity.status(503).body(Map.of("message", "We could not send the verification email. Please try again later."));
                default -> ResponseEntity.badRequest().body(Map.of("message", "Unable to resend verification code."));
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<?> verifyRegistrationOtp(@RequestBody RegistrationOtpRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        try {
            User user = registrationVerificationService.verifyOtp(request.getEmail(), request.getOtp());
            UserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            return ResponseEntity.ok(userService.toAuthResponse(user));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
public ResponseEntity<AuthResponse> login(
        @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

    Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
            )
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    securityContextRepository.saveContext(
            context,
            httpRequest,
            httpResponse
    );

    User user = userService.findByEmail(request.getEmail());

    return ResponseEntity.ok(userService.toAuthResponse(user));
}

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        PasswordResetRequestResult result = passwordResetService.requestPasswordReset(request.getEmail());
        return switch (result) {
            case SENT -> ResponseEntity.ok(Map.of("message", "Password reset OTP sent. Check your email."));
            case EMAIL_NOT_FOUND -> ResponseEntity.status(404).body(Map.of("message", "No account exists with that email address."));
            case UNSUPPORTED_ACCOUNT -> ResponseEntity.badRequest().body(Map.of("message", "This account uses Google sign-in and cannot reset a local password."));
            case COOLDOWN_ACTIVE -> ResponseEntity.status(429).body(Map.of("message", "Please wait one minute before requesting another OTP."));
            case DELIVERY_FAILED -> ResponseEntity.status(503).body(Map.of("message", "We could not send the reset email. Please try again later."));
        };
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, Object>> verifyResetOtp(@RequestBody VerifyResetOtpRequest request) {
        boolean valid = passwordResetService.verifyResetOtp(request.getEmail(), request.getOtp());
        if (!valid) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Invalid or expired OTP"));
        }
        return ResponseEntity.ok(Map.of("valid", true, "message", "OTP verified successfully"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now sign in with your new password."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        String email = authentication.getName();
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.toAuthResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(
            Authentication authentication,
            @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        if (!request.isConfirmed()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please confirm account deletion."));
        }
        try {
            User user = userService.findByEmail(authentication.getName());
            if ("GOOGLE".equalsIgnoreCase(user.getProvider())) {
                if (!hasRecentGoogleDeletionReauthentication(httpRequest.getSession(false), user.getEmail())) {
                    return ResponseEntity.status(409).body(Map.of(
                            "code", "GOOGLE_REAUTHENTICATION_REQUIRED",
                            "message", "Please reauthenticate with Google before account deletion.",
                            "reauthenticationRequired", true));
                }
                userService.deleteGoogleAccount(user.getEmail());
            } else {
                userService.deleteLocalAccount(user.getEmail(), request.getPassword());
            }
            HttpSession session = httpRequest.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        } catch (IncorrectPasswordException e) {
            return ResponseEntity.status(401).body(Map.of("code", "INCORRECT_PASSWORD", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage(), "reauthenticationRequired", true));
        }
    }

    @GetMapping("/account/google-reauth")
    public ResponseEntity<?> startGoogleDeletionReauthentication(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByEmail(authentication.getName());
        if (!"GOOGLE".equalsIgnoreCase(user.getProvider())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This account does not use Google sign-in."));
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_EMAIL, user.getEmail());
        session.removeAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_AT);
        return ResponseEntity.ok(Map.of("authorizationUrl", "/oauth2/authorization/google"));
    }

    private boolean hasRecentGoogleDeletionReauthentication(HttpSession session, String email) {
        if (session == null || !email.equalsIgnoreCase((String) session.getAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_EMAIL))) {
            return false;
        }
        Object reauthenticatedAt = session.getAttribute(AccountDeletionOAuthSuccessHandler.DELETE_REAUTH_AT);
        if (!(reauthenticatedAt instanceof Long timestamp) || Instant.now().toEpochMilli() - timestamp > 5 * 60_000L) {
            return false;
        }
        return true;
    }

    private Map<String, String> existingEmailResponse() {
        return Map.of("code", "EMAIL_ALREADY_EXISTS", "message", "Email already exists.");
    }
}
