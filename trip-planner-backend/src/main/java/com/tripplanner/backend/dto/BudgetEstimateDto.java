package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BudgetEstimateDto {
    private String budgetCategory; // "Budget", "Standard", "Premium"
    
    private double minAccommodation;
    private double maxAccommodation;
    
    private double minTransport;
    private double maxTransport;
    
    private double minFood;
    private double maxFood;
    
    private double minAttractions;
    private double maxAttractions;
    
    private double minTotal;
    private double maxTotal;
    
    private String currency; // "INR"
    private String assumptions;
}
