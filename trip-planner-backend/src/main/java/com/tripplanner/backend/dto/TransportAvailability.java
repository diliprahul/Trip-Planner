package com.tripplanner.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TransportAvailability {
    private boolean train;
    private boolean bus;
    private boolean flight;
}
