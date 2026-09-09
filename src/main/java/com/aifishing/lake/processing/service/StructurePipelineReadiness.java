package com.aifishing.lake.processing.service;

import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;

public record StructurePipelineReadiness(
        Pipeline pipeline,
        StructurePipelineAvailability availability,
        boolean available,
        String analysisVersion,
        long featureCount,
        LakeAnalysisRun run
) {
    public static StructurePipelineReadiness of(
            Pipeline pipeline,
            StructurePipelineAvailability availability,
            String analysisVersion,
            long featureCount,
            LakeAnalysisRun run
    ) {
        return new StructurePipelineReadiness(
                pipeline,
                availability,
                availability == StructurePipelineAvailability.READY,
                analysisVersion,
                featureCount,
                run
        );
    }

    public String generatePlanErrorCode() {
        return switch (availability) {
            case READY -> null;
            case EMPTY -> "STRUCTURE_NONE_AFTER_ANALYSIS";
            case STALE -> "STALE_OR_MISSING_FEATURE_SNAPSHOT";
            case PROVENANCE_INVALID -> "STRUCTURE_PIPELINE_PROVENANCE_INVALID";
            case FAILED, NOT_PROCESSED -> "STRUCTURE_PIPELINE_NOT_READY";
        };
    }

    public String generatePlanErrorMessage() {
        return switch (availability) {
            case READY -> null;
            case NOT_PROCESSED -> pipeline + " structure analysis has not been processed for this lake.";
            case FAILED -> pipeline + " structure analysis failed for this lake.";
            case EMPTY -> pipeline + " analysis produced no structure features.";
            case STALE -> pipeline + " structure snapshot is stale relative to current lake data.";
            case PROVENANCE_INVALID -> pipeline + " structure snapshot is missing required provenance.";
        };
    }
}
