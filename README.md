Trip Planner Application

The Trip Planner Application is a full-stack web application designed to generate structured, day-wise travel itineraries based on user input. The primary objective of this project is to simplify trip planning by allowing users to enter essential travel details and receive an organized itinerary that includes daily plans, hotel suggestions, and transport-related information. The application demonstrates seamless integration between a modern frontend and a scalable backend, following industry-standard development practices.

The frontend is developed using React with Vite, providing a fast and efficient development environment along with a responsive user interface. Users can interact with a dynamic trip creation form, and the application validates input data before sending requests to the backend. The generated itinerary is rendered dynamically, ensuring a smooth and interactive user experience. Centralized state management is used to maintain consistency across components and manage API-driven data flow.

The backend is implemented using Spring Boot and follows a layered architecture consisting of controllers, services, repositories, entities, and DTOs. RESTful APIs handle trip creation and itinerary generation, while the service layer encapsulates core business logic. DTO-based request and response models improve maintainability and decouple internal data structures from external contracts. CORS configuration ensures smooth communication between the frontend and backend during development.

Key Highlights

Full-stack architecture using React (Vite) and Spring Boot

RESTful API design with clean controller and service separation

DTO-driven request and response handling for maintainable APIs

Dynamic, day-wise itinerary generation logic

Frontend-backend integration with proper CORS configuration

Clean project structure and Git-based version control practices
