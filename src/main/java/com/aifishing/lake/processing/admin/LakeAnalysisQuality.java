package com.aifishing.lake.processing.admin;

import java.util.List;
import java.util.Map;

public record LakeAnalysisQuality(
        String processingStatus,
        String currentAnalysisVersion,
        String analysisRunStatus,
        Long processingDurationMs,
        Integer contourCount,
        Integer bathymetryPointCount,
        Map<String, String> bathymetryAvailability,
        Map<String, Integer> featureCountByType,
        Double averageConfidence,
        Map<String, Integer> confidenceDistribution,
        List<String> notAvailableFeatureTypes,
        List<String> failedFeatureTypes,
        List<String> warnings
) {
}
