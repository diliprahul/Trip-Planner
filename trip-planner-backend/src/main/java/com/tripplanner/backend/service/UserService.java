package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.AuthResponse;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.User;

public interface UserService {
    AuthResponse register(RegisterRequest request);
    User findByEmail(String email);
    AuthResponse toAuthResponse(User user);
}
