# Technical Handoff Document: Trip Planner Project

This technical handoff document provides a comprehensive architecture, data flow, feature breakdown, integration inventory, runtime debugging analysis, and file reference for the **Trip Planner** project. It is intended for AI coding assistants (such as Codex) and engineers maintaining or debugging the codebase.

---

## 1. Project Overview

**Trip Planner** is a full-stack web application designed to help users create, customize, visualize, and manage personalized travel itineraries. Key capabilities include:
- User registration, authentication (JWT-based), and Google OAuth2 social login.
- User profile management with avatar uploads (stored locally under `/uploads/`, served via Spring Boot static resource handlers).
- Trip creation specifying destination, dates/duration, budget level, and travel style.
- Automated itinerary generation combining OpenStreetMap Nominatim (geocoding), Overpass API (points of interest and accommodation retrieval), and Google Gemini API (fallback/augmentation).
- Weather forecasts for the destination.
- Budget estimation.
- Persistent storage of trips and daily plans in an Aiven Cloud MySQL database.
- Trip history management ("My Trips").

---

## 2. Technology Stack

### Frontend (`trip-planner-frontend/`)
- **Framework:** React with Vite
- **Routing:** React Router (`react-router-dom`)
- **Styling:** Vanilla CSS (`index.css`, component-specific CSS)
- **HTTP Client:** Axios or custom fetch wrappers (`src/api/tripApi.js`)
- **State Management:** React Context (`AuthContext`, `TripContext`)

### Backend (`trip-planner-backend/`)
- **Language / Runtime:** Java 17 / Spring Boot 3.x
- **Build Tool:** Apache Maven (`mvnw`)
- **Database / ORM:** Spring Data JPA / Hibernate, MySQL (Aiven Cloud)
- **Security:** Spring Security with JWT authentication (`JwtAuthenticationFilter`) and OAuth2 Login (`CustomOidcUserService`)
- **External Clients:** `RestTemplate` / `RestClient` for HTTP calls to Nominatim, Overpass API, and Google Gemini API
- **JSON Processing:** Jackson (`ObjectMapper`)
- **Utilities:** Lombok

---

## 3. Repository Structure

```
C:\Users\dilip\Desktop\Trip-Planner-clean
├── package-lock.json
├── README.md
├── trip-planner-backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/
│       │   ├── java/com/tripplanner/backend/
│       │   │   ├── TripPlannerBackendApplication.java
│       │   │   ├── config/ (AppConfig, CorsConfig, SecurityConfig, WebConfig)
│       │   │   ├── controller/ (AuthController, ProfileController, TripController)
│       │   │   ├── dto/ (AuthResponse, CreateTripRequest, DayPlanDto, HotelDto, TripDetailResponse, etc.)
│       │   │   ├── entity/ (DayPlan, HotelSuggestion, PasswordResetToken, Trip, User)
│       │   │   ├── repository/ (DayPlanRepository, HotelSuggestionRepository, TripRepository, UserRepository)
│       │   │   ├── security/ (CustomOidcUserService, CustomUserDetailsService, JwtAuthenticationFilter, etc.)
│       │   │   ├── service/ (GeminiService, GeocodingService, OverpassService, TripService, UserService, WeatherService, etc.)
│       │   │   │   └── impl/ (GeminiServiceImpl, NominatimGeocodingService, OverpassServiceImpl, TripServiceImpl, etc.)
│       │   │   └── util/ (GeoLocation, PlaceResult)
│       │   └── resources/
│       │       ├── application.properties
│       │       └── application-prod.properties
│       └── test/java/com/tripplanner/backend/
│           ├── AuthAndTripSecurityTest.java
│           ├── PasswordResetIntegrationTest.java
│           └── service/impl/ (GeminiServiceImplTest, OverpassServiceImplTest, TripServiceImplTest)
└── trip-planner-frontend/
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.jsx
        ├── main.jsx
        ├── api/tripApi.js
        ├── components/ (ErrorMessage, Footer, Header, Itinerary, TripForm, WeatherForecast)
        ├── context/ (AuthContext, TripContext, useAuth.js, useTrip.js)
        ├── pages/ (Home, Login, MyTrips, NotFound, PrivacyPolicy, Profile, Register, result.jsx, TermsConditions)
        └── utils/validators.js
```

---

## 4. Architecture Diagram

```
[ React Frontend (Vite) ]
       │
       ├───── HTTP /api/* (Proxied by Vite to http://localhost:8089) ─────┐
       │                                                                  ▼
       │                                                      [ Spring Boot Backend ]
       │                                                      ├── Security Filter (JWT / OAuth2)
       │                                                      ├── Controllers (Trip, Auth, Profile)
       │                                                      └── Services
       │                                                           ├── TripServiceImpl
       │                                                           │     ├── NominatimGeocodingService
       │                                                           │     ├── OverpassServiceImpl ──► [Overpass API]
       │                                                           │     └── GeminiServiceImpl   ──► [Gemini API]
       │                                                           └── WeatherServiceImpl
       │                                                                  │
       └───── Static /uploads/** ─────────────────────────────────────────┘
                                                                          ▼
                                                              [ Aiven Cloud MySQL DB ]
```

---

## 5. Important File and Class Reference

### Backend
- **`TripPlannerBackendApplication.java`**: Spring Boot entry point.
- **`SecurityConfig.java`**: Configures HTTP security, CSRF, session management (stateless), and JWT filter registration.
- **`WebConfig.java`**: Configures static resource handlers for local uploads (`/uploads/**`).
- **`TripController.java`**: Handles trip creation (`POST /api/trips`), trip retrieval (`GET /api/trips/{id}`, `GET /api/trips`), and deletion.
- **`AuthController.java`**: Handles user registration (`POST /api/auth/register`), login (`POST /api/auth/login`), password reset flows.
- **`ProfileController.java`**: Manages user profile retrieval, updates, and profile picture uploads (`POST /api/profile/picture`, `DELETE /api/profile/picture`).
- **`TripServiceImpl.java`**: Core orchestrator for trip creation, geocoding invocation, Overpass integration, Gemini fallback, budget estimation, and persistence.
- **`NominatimGeocodingService.java`**: Queries OpenStreetMap Nominatim for latitude, longitude, and bounding box of a destination.
- **`OverpassServiceImpl.java`**: Queries Overpass API endpoints with timeout, fallback endpoints, and retry logic for tourist attractions and hotels.
- **`GeminiServiceImpl.java`**: Calls Google Gemini API (`gemini-1.5-flash`) to generate structured JSON itineraries and hotel suggestions when primary sources or fallbacks are needed.
- **Entities (`User`, `Trip`, `DayPlan`, `HotelSuggestion`)**: JPA entities mapped to Aiven MySQL tables.

### Frontend
- **`App.jsx`**: Sets up React Router paths (`/`, `/login`, `/register`, `/result`, `/my-trips`, `/profile`, etc.) wrapped in `AuthContext` and `TripContext`.
- **`tripApi.js`**: Axios instance configured with base URL, Authorization bearer token interceptor, and API call functions (`createTrip`, `getTrip`, `getUserTrips`, `getProfile`, etc.).
- **`TripForm.jsx`**: Form component collecting destination, start/end dates, budget level, and travel style.
- **`Itinerary.jsx`**: Renders the daily itinerary, places, hotel suggestions, and weather forecast on the results page.
- **`result.jsx`**: Results page component responsible for fetching or displaying generated/saved trip details.
- **`Profile.jsx`**: User profile view and edit screen with avatar upload handling.

---

## 6. API Endpoint Inventory

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/auth/register` | Register a new user | No |
| POST | `/api/auth/login` | Authenticate user & return JWT | No |
| GET | `/api/auth/me` | Get current authenticated user | Yes |
| GET | `/api/profile` | Get user profile details | Yes |
| PUT | `/api/profile` | Update user profile | Yes |
| POST | `/api/profile/picture` | Upload profile picture (Multipart) | Yes |
| DELETE | `/api/profile/picture` | Remove custom profile picture | Yes |
| POST | `/api/trips` | Create and generate a new trip | Yes |
| GET | `/api/trips` | Get all trips for current user | Yes |
| GET | `/api/trips/{id}` | Get specific trip details by ID | Yes |
| DELETE | `/api/trips/{id}` | Delete a trip | Yes |

---

## 7. Frontend Request Flow

1. **User Input:** User fills out `TripForm.jsx` and clicks Generate.
2. **Action Trigger:** `TripContext` or direct API call `createTrip(tripData)` in `tripApi.js` sends a `POST /api/trips` request with JSON payload containing destination, dates, budget, and travel style, attaching the JWT `Authorization: Bearer <token>` header.
3. **Redirection:** Upon success, the response data is stored or passed via route state/ID to `result.jsx`.
4. **Display:** `result.jsx` renders `Itinerary.jsx`, displaying daily plans, hotels, budget estimates, and weather. If loading an existing trip (`GET /api/trips/{id}`), `result.jsx` fetches the saved trip record directly from the backend.

---

## 8. Backend Request Flow (`POST /api/trips`)

1. **Controller:** `TripController.createTrip` receives `CreateTripRequest` and extracts the authenticated `User` from security context.
2. **Service Orchestration (`TripServiceImpl.createTrip`):**
   - Saves initial `Trip` entity in database with status/placeholder data if needed.
   - **Geocoding:** Calls `GeocodingService` (`NominatimGeocodingService`) to resolve destination coordinates (`GeoLocation`).
   - **Weather:** Calls `WeatherService` to fetch forecasts for the destination coordinates.
   - **Places & Itinerary:** Calls `OverpassService` to search for attractions and tourist spots. If Overpass returns empty or fails, falls back to `GeminiService` (`generateItinerary`).
   - **Hotels:** Calls `OverpassService` for accommodation suggestions. If empty/fails, falls back to `GeminiService` (`generateHotels`).
   - **Budget & Days:** Computes budget estimates and maps daily plans (`DayPlan` entities).
   - **Persistence:** Saves the fully populated `Trip`, `DayPlan` list, and `HotelSuggestion` list to MySQL.
3. **Response:** Returns `TripDetailResponse` DTO containing destination, dates, itinerary days, hotels, weather, and budget estimate.

---

## 9. Database Entities and Relationships

- **`User`**: Id, email, password (hashed), name, favouritePlace, cityOfResidence, customPicture, profilePictureUrl, createdAt, etc. One-to-many relationship with `Trip`.
- **`Trip`**: Id, destination, startDate, endDate, budgetLevel, travel style, coordinates (latitude, longitude), user (ManyToOne), dailyPlans (OneToMany, cascade ALL), hotels (OneToMany, cascade ALL), weather info, budgetEstimate.
- **`DayPlan`**: Id, dayNumber, title, description, trip (ManyToOne).
- **`HotelSuggestion`**: Id, name, address, rating, priceRange, latitude, longitude, trip (ManyToOne).
- **`PasswordResetToken`**: Token management for password recovery.

---

## 10. Aiven Cloud MySQL Configuration

- **Connection Setup:** Configured via Spring Boot datasource properties in `application.properties` and overridden/configured via environment variables or profile settings (`application-prod.properties`).
- **Datasource Properties:**
  - `spring.datasource.url`
  - `spring.datasource.username`
  - `spring.datasource.password`
  - `spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver`
  - `spring.jpa.hibernate.ddl-auto=update` (or `validate`)

---

## 11. External API Integrations

### 1. OpenStreetMap Nominatim
- **URL:** `https://nominatim.openstreetmap.org/search`
- **Purpose:** Geocoding destination names into lat/lon and bounding boxes.
- **Behavior:** Requires User-Agent header to avoid blocking. Timeout configured via RestTemplate/RestClient settings.

### 2. Overpass API
- **Endpoints:**
  - `https://overpass-api.de/api/interpreter`
  - `https://lz4.overpass-api.de/api/interpreter`
  - `https://z.overpass-api.de/api/interpreter`
- **Purpose:** Fetching tourism nodes/ways (attractions, hotels) via Overpass QL queries.
- **Behavior:** Subject to rate limits and timeouts if public servers are overloaded. Implements fallback across alternative endpoints and retry logic.

### 3. Google Gemini API
- **Model:** `gemini-1.5-flash` (`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=...`)
- **Purpose:** Fallback / AI generation for itineraries and hotel suggestions when Overpass returns insufficient results or fails.

---

## 12. Itinerary Generation Process

- The system queries Overpass for tourism/attraction elements in the bounding box of the destination.
- If Overpass times out or returns fewer items than required for the trip duration, `GeminiService.generateItinerary` is invoked as a fallback.
- Daily plans are structured into `DayPlan` entities linking day numbers to specific attraction names and descriptions.

---

## 13. Hotel Retrieval Process

- Overpass queries search for nodes/ways with tags like `tourism=hotel`, `tourism=hostel`, `tourism=guest_house`.
- If Overpass fails or returns no hotels, `GeminiServiceImpl.generateHotels` provides fallback hotel recommendations.

---

## 14. Gemini Fallback Behavior

- If `google.gemini.api.key` is missing or blank, `GeminiServiceImpl` logs a warning and returns an empty list (`Collections.emptyList()`).
- When called, it constructs a strict JSON prompt asking for structured itinerary or hotel arrays, cleans markdown code blocks (` ```json `), and deserializes via Jackson `ObjectMapper`.

---

## 15. Authentication Flow

- **Local Login:** Users submit credentials to `POST /api/auth/login`. Upon success, backend generates a JWT token. Frontend stores the token and attaches it to subsequent requests.
- **OAuth2 Login:** Users authenticate via Google OAuth2 (`/oauth2/authorization/google`), handled by Spring Security `CustomOidcUserService`.
- **Stateless Session:** Spring Security is configured with stateless session policy (`SessionCreationPolicy.STATELESS`), validating tokens via `JwtAuthenticationFilter`.

---

## 16. Configuration and Environment-Variable Names

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (Database)
- `GOOGLE_GEMINI_API_KEY` (Gemini API key)
- `JWT_SECRET` (JWT signing secret)
- `APP_UPLOAD_DIR` (Upload directory for profile pictures, default `uploads`)

---

## 17. Existing Git Modifications

Modified files in the working tree include:
- **`WebConfig.java`**: Enhanced robustness for static upload directory creation.
- **`GeminiService.java` & `GeminiServiceImpl.java`**: Added `generateHotels(...)` method and robust exception handling (`RestClientException`).
- **`NominatimGeocodingService.java` & `OverpassServiceImpl.java`**: Geocoding and Overpass query improvements, endpoint fallback, and timeout handling.
- **`TripServiceImpl.java`**: Integration of robust fallbacks and error handling during trip generation.
- **`tripApi.js`, `Header.jsx`, `Itinerary.jsx`, `Profile.jsx`, `vite.config.js`**: Frontend enhancements for profile picture loading, avatar error handling (`imgError`), proxy config for `/uploads`, and UI responsiveness.

---

## 18. Current Bugs and Evidence

Observed symptoms and potential causes:
1. **Slow Loading / Timeouts:** Overpass public API endpoints (`overpass-api.de`, `lz4.overpass-api.de`) frequently encounter connection timeouts or rate limiting under heavy load, causing requests to hang for minutes.
2. **Empty Itinerary / Missing Hotels:** When Overpass fails and Gemini API key is not configured or fails, fallback lists return empty, resulting in empty itineraries or no named accommodations.
3. **HTTP 500 Errors:** Occur when external API failures propagate unhandled exceptions or when database transactions encounter connection issues (e.g., Hibernate dialect/metadata warnings against Aiven MySQL).

---

## 19. Build and Test Status

- **Backend Build:** `.\mvnw.cmd -DskipTests compile` succeeds (`BUILD SUCCESS`).
- **Frontend Build:** `npm run build` succeeds (`SUCCESS`).
- **Tests:** Some integration/service tests may encounter Hibernate dialect or external network dependency issues if run without proper test profiles or mocks.

---

## 20. Known Limitations

- Reliance on public Overpass API endpoints makes trip generation susceptible to public rate limits and network latency.
- Gemini fallback requires a valid `google.gemini.api.key` property; without it, empty external data cannot be automatically augmented by AI.

---

## 21. Recommended Next Debugging Steps

1. Inspect Overpass timeout configuration and consider adding request caching or circuit breakers.
2. Verify if `GOOGLE_GEMINI_API_KEY` is correctly populated in environment variables to ensure robust fallback when Overpass times out.
3. Add mock test coverage for Overpass and Gemini service failures to verify graceful degradation.

---

## 22. Exact Files Codex Should Inspect First

1. **`trip-planner-backend/src/main/java/com/tripplanner/backend/service/impl/TripServiceImpl.java`** (Orchestration logic and fallback handling)
2. **`trip-planner-backend/src/main/java/com/tripplanner/backend/service/impl/OverpassServiceImpl.java`** (External Overpass queries and timeouts)
3. **`trip-planner-backend/src/main/java/com/tripplanner/backend/service/impl/GeminiServiceImpl.java`** (AI fallback generation)
4. **`trip-planner-frontend/src/api/tripApi.js`** (API communication layer)
5. **`trip-planner-frontend/src/components/Itinerary.jsx`** & **`trip-planner-frontend/src/pages/result.jsx`** (Frontend display of itineraries and hotels)
