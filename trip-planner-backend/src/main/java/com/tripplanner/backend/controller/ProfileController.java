package com.tripplanner.backend.controller;

import com.tripplanner.backend.dto.AuthResponse;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:*", allowCredentials = "true")
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<AuthResponse> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(userService.toAuthResponse(user));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Authentication authentication, @RequestBody Map<String, String> request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        try {
            String name = request.get("name");
            String cityOfResidence = request.get("cityOfResidence");
            String favouritePlace = request.get("favouritePlace");
            User updatedUser = userService.updateProfile(authentication.getName(), name, cityOfResidence, favouritePlace);
            return ResponseEntity.ok(userService.toAuthResponse(updatedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/picture")
    public ResponseEntity<?> uploadPicture(Authentication authentication, @RequestParam("file") MultipartFile file) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        try {
            User updatedUser = userService.uploadProfilePicture(authentication.getName(), file);
            return ResponseEntity.ok(userService.toAuthResponse(updatedUser));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/picture")
    public ResponseEntity<?> removePicture(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        try {
            User updatedUser = userService.removeProfilePicture(authentication.getName());
            return ResponseEntity.ok(userService.toAuthResponse(updatedUser));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }
}
