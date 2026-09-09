package com.aifishing.planning.dto;

import com.aifishing.planning.domain.PlanningRunStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GeneratePlanResponse(
        UUID planningRunId,
        PlanningRunStatus status,
        String errorMessage,
        List<String> warnings,
        Map<String, Object> filterSummary,
        TripPlanResponse plan
) {
}
