package com.aifishing.lake.api;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;

import java.util.Map;
import java.util.UUID;

public record LakePlanningCapabilitiesResponse(
        UUID lakeId,
        Map<Pipeline, PipelineCapability> pipelines
) {
    public record PipelineCapability(
            boolean available,
            String analysisVersion,
            String status,
            StructurePipelineAvailability unavailableReason
    ) {
    }
}
