package com.aifishing.boat.api;

import com.aifishing.boat.capability.CapabilityMetric;
import com.aifishing.boat.capability.ResolvedBoatCapability;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.WindWaveCapability;

import java.util.List;

public record ResolvedBoatCapabilityPreview(
        MetricPreview cruiseSpeedKmh,
        MetricPreview estimatedPracticalRangeKm,
        MetricPreview windWaveCapability,
        List<String> warnings,
        List<String> extractionGaps,
        List<String> missingHints
) {
    public ResolvedBoatCapabilityPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        extractionGaps = extractionGaps == null ? List.of() : List.copyOf(extractionGaps);
        missingHints = missingHints == null ? List.of() : List.copyOf(missingHints);
    }

    public static ResolvedBoatCapabilityPreview from(ResolvedBoatCapability resolved) {
        if (resolved == null) {
            return null;
        }
        return new ResolvedBoatCapabilityPreview(
                numberPreview(resolved.cruiseSpeedKmh()),
                numberPreview(resolved.estimatedPracticalRangeKm()),
                windPreview(resolved.windWaveCapability()),
                resolved.warnings(),
                resolved.extractionGaps(),
                resolved.missingHints()
        );
    }

    private static MetricPreview numberPreview(CapabilityMetric<Double> metric) {
        if (metric == null) {
            return null;
        }
        return new MetricPreview(
                metric.value(),
                metric.confidence(),
                metric.source(),
                null
        );
    }

    private static MetricPreview windPreview(CapabilityMetric<WindWaveCapability> metric) {
        if (metric == null) {
            return null;
        }
        return new MetricPreview(
                null,
                metric.confidence(),
                metric.source(),
                metric.value() == null ? null : metric.value().name()
        );
    }

    public record MetricPreview(
            Double value,
            Double confidence,
            CapabilitySource source,
            String label
    ) {
    }
}
