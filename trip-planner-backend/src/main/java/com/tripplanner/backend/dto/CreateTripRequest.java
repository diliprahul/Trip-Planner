package com.tripplanner.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateTripRequest {

    private String origin;
    private String destination;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    // ✅ MUST EXIST
    private List<String> categories;
}
