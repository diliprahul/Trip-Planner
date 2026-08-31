package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DayPlanRepository extends JpaRepository<DayPlan, Long> {

    List<DayPlan> findByTripOrderByDayNumberAsc(Trip trip);
    void deleteByTrip(Trip trip);
}
