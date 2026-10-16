package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.dto.AuthResponse;
import com.tripplanner.backend.dto.RegisterRequest;
import com.tripplanner.backend.entity.User;
import com.tripplanner.backend.repository.UserRepository;
import com.tripplanner.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email is already registered");
        }
        if (request.getCityOfResidence() != null && request.getCityOfResidence().trim().length() > 100) {
            throw new IllegalArgumentException("City of residence cannot exceed 100 characters");
        }
        if (request.getFavouritePlace() != null && request.getFavouritePlace().trim().length() > 100) {
            throw new IllegalArgumentException("Favourite place or city cannot exceed 100 characters");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .cityOfResidence(request.getCityOfResidence() != null && !request.getCityOfResidence().isBlank() ? request.getCityOfResidence().trim() : null)
                .favouritePlace(request.getFavouritePlace() != null && !request.getFavouritePlace().isBlank() ? request.getFavouritePlace().trim() : null)
                .build();

        User savedUser = userRepository.save(user);
        return toAuthResponse(savedUser);
    }

    @Override
    public User findByEmail(String email) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : null;
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    @Override
    public AuthResponse toAuthResponse(User user) {
        String effectivePicture = null;
        if (user.getCustomPicture() != null && !user.getCustomPicture().isBlank()) {
            effectivePicture = "/uploads/" + user.getCustomPicture();
        } else if (user.getGooglePictureUrl() != null && !user.getGooglePictureUrl().isBlank()) {
            effectivePicture = user.getGooglePictureUrl();
        }

        return AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider())
                .createdAt(user.getCreatedAt())
                .customPicture(user.getCustomPicture())
                .googlePictureUrl(user.getGooglePictureUrl())
                .profilePictureUrl(effectivePicture)
                .cityOfResidence(user.getCityOfResidence())
                .favouritePlace(user.getFavouritePlace())
                .build();
    }

    @Override
    public User processOAuthPostLogin(String provider, String providerId, String email, String name, boolean emailVerified) {
        return processOAuthPostLogin(provider, providerId, email, name, emailVerified, null);
    }

    @Override
    public User processOAuthPostLogin(String provider, String providerId, String email, String name, boolean emailVerified, String pictureUrl) {
        if (!emailVerified || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google account email is missing or not verified.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // 1. Check if user already exists with provider and providerId
        var existingOAuthUser = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existingOAuthUser.isPresent()) {
            User user = existingOAuthUser.get();
            boolean updated = false;
            if (name != null && !name.isBlank() && !name.equals(user.getName())) {
                user.setName(name.trim());
                updated = true;
            }
            if (pictureUrl != null && !pictureUrl.isBlank() && !pictureUrl.equals(user.getGooglePictureUrl())) {
                user.setGooglePictureUrl(pictureUrl.trim());
                updated = true;
            }
            if (updated) {
                user = userRepository.save(user);
            }
            return user;
        }

        // 2. Check if a user exists with this email (e.g. local email/password account)
        var existingEmailUser = userRepository.findByEmail(normalizedEmail);
        if (existingEmailUser.isPresent()) {
            User user = existingEmailUser.get();
            if (user.getProvider() == null || "LOCAL".equalsIgnoreCase(user.getProvider())) {
                user.setProvider(provider);
                user.setProviderId(providerId);
                if (name != null && !name.isBlank() && (user.getName() == null || user.getName().isBlank())) {
                    user.setName(name.trim());
                }
                if (pictureUrl != null && !pictureUrl.isBlank() && (user.getGooglePictureUrl() == null || user.getGooglePictureUrl().isBlank())) {
                    user.setGooglePictureUrl(pictureUrl.trim());
                }
                return userRepository.save(user);
            } else if (!provider.equalsIgnoreCase(user.getProvider())) {
                throw new IllegalStateException("An account with email " + normalizedEmail + " is already linked to another provider.");
            }
            if (pictureUrl != null && !pictureUrl.isBlank() && (user.getGooglePictureUrl() == null || user.getGooglePictureUrl().isBlank())) {
                user.setGooglePictureUrl(pictureUrl.trim());
                return userRepository.save(user);
            }
            return user;
        }

        // 3. Create new OAuth user
        User newUser = User.builder()
                .name(name != null && !name.isBlank() ? name.trim() : normalizedEmail)
                .email(normalizedEmail)
                .provider(provider)
                .providerId(providerId)
                .googlePictureUrl(pictureUrl != null ? pictureUrl.trim() : null)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .build();

        return userRepository.save(newUser);
    }

    @Override
    public User updateProfile(String email, String name, String cityOfResidence, String favouritePlace) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("Full name cannot exceed 100 characters");
        }
        if (cityOfResidence != null && cityOfResidence.trim().length() > 100) {
            throw new IllegalArgumentException("City of residence cannot exceed 100 characters");
        }
        if (favouritePlace != null && favouritePlace.trim().length() > 100) {
            throw new IllegalArgumentException("Favourite place or city cannot exceed 100 characters");
        }

        User user = findByEmail(email);
        user.setName(name.trim());
        user.setCityOfResidence(cityOfResidence != null && !cityOfResidence.isBlank() ? cityOfResidence.trim() : null);
        user.setFavouritePlace(favouritePlace != null && !favouritePlace.isBlank() ? favouritePlace.trim() : null);
        return userRepository.save(user);
    }

    @Override
    public User uploadProfilePicture(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select an image file to upload");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds the 5 MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") && !ext.equals(".webp")) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported");
        }

        // Validate actual decoded image content
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new IllegalArgumentException("Invalid or malformed image file content");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode image file: " + e.getMessage());
        }

        User user = findByEmail(email);
        String oldCustomPicture = user.getCustomPicture();

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String newFilename = UUID.randomUUID().toString() + ext;
            Path filePath = uploadPath.resolve(newFilename);
            file.transferTo(filePath.toFile());

            user.setCustomPicture(newFilename);
            User savedUser = userRepository.save(user);

            // Clean up old custom picture safely if no other user references it
            if (oldCustomPicture != null && !oldCustomPicture.isBlank()) {
                try {
                    long count = userRepository.countByCustomPicture(oldCustomPicture);
                    if (count <= 0) {
                        Path oldPath = uploadPath.resolve(oldCustomPicture);
                        Files.deleteIfExists(oldPath);
                    }
                } catch (Exception ignored) {
                    // Non-fatal cleanup failure safeguard
                }
            }

            return savedUser;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store profile picture: " + e.getMessage(), e);
        }
    }

    @Override
    public User removeProfilePicture(String email) {
        User user = findByEmail(email);
        String oldCustomPicture = user.getCustomPicture();
        user.setCustomPicture(null);
        User savedUser = userRepository.save(user);

        if (oldCustomPicture != null && !oldCustomPicture.isBlank()) {
            try {
                Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
                long count = userRepository.countByCustomPicture(oldCustomPicture);
                if (count <= 0) {
                    Path oldPath = uploadPath.resolve(oldCustomPicture);
                    Files.deleteIfExists(oldPath);
                }
            } catch (Exception ignored) {
                // Non-fatal cleanup failure safeguard
            }
        }

        return savedUser;
    }
}
