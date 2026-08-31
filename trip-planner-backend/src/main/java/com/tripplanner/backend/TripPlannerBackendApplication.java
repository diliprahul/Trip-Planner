package com.tripplanner.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.tripplanner.backend.repository")
public class TripPlannerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripPlannerBackendApplication.class, args);
    }
}
