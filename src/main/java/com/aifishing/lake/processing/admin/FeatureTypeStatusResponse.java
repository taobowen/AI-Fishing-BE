package com.aifishing.lake.processing.admin;

import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;

import java.time.Instant;

public record FeatureTypeStatusResponse(
        FeatureType type,
        FeatureStatusCode status,
        Integer recordCount,
        Instant lastAttemptedAt,
        Instant lastSuccessfulAnalysisAt,
        String errorMessage,
        Double averageConfidence
) {
}
