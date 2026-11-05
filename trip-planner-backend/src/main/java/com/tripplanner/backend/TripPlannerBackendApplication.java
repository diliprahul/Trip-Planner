package com.tripplanner.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.tripplanner.backend.repository")
@EnableScheduling
public class TripPlannerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripPlannerBackendApplication.class, args);
    }
}
