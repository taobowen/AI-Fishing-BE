package com.aifishing.lake.processing.admin;

import com.aifishing.lake.processing.dto.Pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LakeProcessSummaryResponse(
        UUID lakeId,
        String lakeName,
        Pipeline pipeline,
        String processingStatus,
        String analysisVersion,
        String algorithmVersion,
        String analysisRunStatus,
        long durationMs,
        Instant startedAt,
        Instant completedAt,
        int contourCount,
        int bathymetryPointCount,
        Map<String, String> bathymetryAvailability,
        Map<String, Integer> featureCountByType,
        Double averageConfidence,
        Map<String, Integer> confidenceDistribution,
        List<FeatureTypeStatusResponse> features,
        List<String> notAvailableFeatureTypes,
        List<String> failedFeatureTypes,
        List<String> warnings
) {
}
