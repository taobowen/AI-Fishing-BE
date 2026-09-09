package com.aifishing.strategy.dto;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRunStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StrategyRunResponse(
        UUID id,
        UUID tripId,
        StrategyRunStatus status,
        String modelId,
        String promptVersion,
        Pipeline featurePipeline,
        Instant startedAt,
        Instant completedAt,
        FishingStrategyProfile strategyProfile,
        Map<String, Object> sourceMetadata,
        Map<String, Object> usageMetadata,
        String errorMessage
) {
}
