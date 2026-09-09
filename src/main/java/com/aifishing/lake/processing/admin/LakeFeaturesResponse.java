package com.aifishing.lake.processing.admin;

import com.aifishing.lake.processing.dto.Pipeline;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LakeFeaturesResponse(
        UUID lakeId,
        String lakeName,
        Pipeline pipeline,
        String processingStatus,
        String currentAnalysisVersion,
        Map<String, Integer> featureCountByType,
        Double averageConfidence,
        Map<String, Integer> confidenceDistribution,
        List<FeatureTypeStatusResponse> features,
        List<String> warnings
) {
}
