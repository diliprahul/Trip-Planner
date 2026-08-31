package com.tripplanner.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TransportLink {
    private String type;   // TRAIN / BUS / FLIGHT
    private String url;
}
