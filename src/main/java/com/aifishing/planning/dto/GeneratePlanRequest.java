package com.aifishing.planning.dto;

import com.aifishing.lake.processing.dto.Pipeline;

import java.util.UUID;

public record GeneratePlanRequest(
        UUID strategyRunId,
        UUID accessPointId,
        Pipeline featurePipeline
) {
}
