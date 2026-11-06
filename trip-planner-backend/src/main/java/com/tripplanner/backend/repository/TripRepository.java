package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.Trip;
import com.tripplanner.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :tripId")
    Optional<Trip> findByIdForUpdate(@Param("tripId") Long tripId);

    List<Trip> findByUserOrderByCreatedAtDesc(User user);
    List<Trip> findByUser(User user);

    @Query("select t.latitude as latitude, t.longitude as longitude, t.startDate as startDate, t.endDate as endDate " +
            "from Trip t where t.id = :tripId and lower(t.user.email) = lower(:email)")
    Optional<TripWeatherData> findWeatherDataByTripIdAndOwnerEmail(
            @Param("tripId") Long tripId,
            @Param("email") String email);

    interface TripWeatherData {
        Double getLatitude();
        Double getLongitude();
        java.time.LocalDate getStartDate();
        java.time.LocalDate getEndDate();
    }
}
