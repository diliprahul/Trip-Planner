package com.tripplanner.backend.repository;

import com.tripplanner.backend.entity.CityCoordinateCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityCoordinateCacheRepository extends JpaRepository<CityCoordinateCache, String> {}
