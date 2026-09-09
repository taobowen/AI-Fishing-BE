package com.aifishing.boat.capability;

import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.WindWaveCapability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResolvedBoatCapability(
        CapabilityMetric<Double> cruiseSpeedKmh,
        CapabilityMetric<Double> estimatedPracticalRangeKm,
        CapabilityMetric<WindWaveCapability> windWaveCapability,
        List<String> warnings,
        List<String> extractionGaps,
        List<String> missingHints,
        UUID cacheProfileId,
        String fingerprint,
        String resolverVersion,
        Instant resolvedAt
) {
    public ResolvedBoatCapability {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        extractionGaps = extractionGaps == null ? List.of() : List.copyOf(extractionGaps);
        missingHints = missingHints == null ? List.of() : List.copyOf(missingHints);
    }

    public static ResolvedBoatCapability empty(String fingerprint, String resolverVersion) {
        return new ResolvedBoatCapability(
                CapabilityMetric.of(null, null, CapabilitySource.CONSERVATIVE_FALLBACK),
                CapabilityMetric.of(null, 0.0, CapabilitySource.CONSERVATIVE_FALLBACK),
                CapabilityMetric.of(WindWaveCapability.LOW, 0.3, CapabilitySource.CONSERVATIVE_FALLBACK),
                new ArrayList<>(),
                List.of(),
                List.of(),
                null,
                fingerprint,
                resolverVersion,
                Instant.now()
        );
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cruiseSpeedKmh", metricSnapshot(cruiseSpeedKmh));
        map.put("estimatedPracticalRangeKm", metricSnapshot(estimatedPracticalRangeKm));
        map.put("windWaveCapability", metricSnapshot(windWaveCapability));
        map.put("warnings", warnings);
        return map;
    }

    private static Map<String, Object> metricSnapshot(CapabilityMetric<?> metric) {
        Map<String, Object> map = new LinkedHashMap<>();
        Object value = metric == null ? null : metric.value();
        map.put("value", value instanceof Enum<?> enumerated ? enumerated.name() : value);
        map.put("confidence", metric == null ? null : metric.confidence());
        map.put("source", metric == null || metric.source() == null ? null : metric.source().name());
        return map;
    }
}
