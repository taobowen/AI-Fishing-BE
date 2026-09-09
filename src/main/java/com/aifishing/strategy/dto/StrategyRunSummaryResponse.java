package com.aifishing.strategy.dto;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.StrategyRunStatus;

import java.time.Instant;
import java.util.UUID;

public record StrategyRunSummaryResponse(
        UUID id,
        UUID tripId,
        StrategyRunStatus status,
        String modelId,
        String promptVersion,
        Pipeline featurePipeline,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
