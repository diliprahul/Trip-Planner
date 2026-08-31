package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.HotelSuggestion;
import com.tripplanner.backend.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelSuggestionRepository extends JpaRepository<HotelSuggestion, Long> {

    List<HotelSuggestion> findByTrip(Trip trip);

    void deleteByTrip(Trip trip);
}
