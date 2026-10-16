package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.AuthResponse;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    AuthResponse register(RegisterRequest request);
    User findByEmail(String email);
    AuthResponse toAuthResponse(User user);
    User processOAuthPostLogin(String provider, String providerId, String email, String name, boolean emailVerified);
    User processOAuthPostLogin(String provider, String providerId, String email, String name, boolean emailVerified, String pictureUrl);
    User updateProfile(String email, String name, String cityOfResidence, String favouritePlace);
    User uploadProfilePicture(String email, MultipartFile file);
    User removeProfilePicture(String email);
}
