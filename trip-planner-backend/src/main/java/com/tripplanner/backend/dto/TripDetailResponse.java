package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TripDetailResponse {

    private Long id;
    private String origin;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private int days;
    private Double latitude;
    private Double longitude;

    private List<DayPlanDto> dayPlans;
    private List<HotelDto> hotels;
    private List<BudgetEstimateDto> budgetEstimates;
    private WeatherResponseDto weatherResponse;
}
