package com.tripplanner.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CoordinateBackfillReport {
    Long tripId;
    boolean dryRun;
    int updated;
    int skipped;
    int failed;
}
