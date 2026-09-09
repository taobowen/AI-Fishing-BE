package com.aifishing.planning.dto;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.domain.PlanningRunStatus;

import java.time.Instant;
import java.util.UUID;

public record PlanningRunSummaryResponse(
        UUID id,
        UUID tripId,
        UUID strategyRunId,
        PlanningRunStatus status,
        Pipeline featurePipeline,
        String featureAnalysisVersion,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
