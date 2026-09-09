package com.aifishing.planning.dto;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.domain.PlanningRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlanningRunResponse(
        UUID id,
        UUID tripId,
        UUID strategyRunId,
        PlanningRunStatus status,
        Pipeline featurePipeline,
        String featureAnalysisVersion,
        String algorithmVersion,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> filterSummary,
        List<String> warnings,
        Map<String, Object> usageMetadata,
        String errorMessage
) {
}
