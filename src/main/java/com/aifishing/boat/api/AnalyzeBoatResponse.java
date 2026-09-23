package com.aifishing.boat.api;

import com.aifishing.boat.capability.ResolvedBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;

import java.util.List;

public record AnalyzeBoatResponse(
        BoatCapabilityProposal proposal,
        List<String> warnings,
        List<String> extractionGaps,
        List<String> missingHints
) {
    public AnalyzeBoatResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        extractionGaps = extractionGaps == null ? List.of() : List.copyOf(extractionGaps);
        missingHints = missingHints == null ? List.of() : List.copyOf(missingHints);
    }

    public static AnalyzeBoatResponse from(ResolvedBoatCapability resolved) {
        if (resolved == null) {
            return new AnalyzeBoatResponse(new BoatCapabilityProposal(null, null, null), List.of(), List.of(), List.of());
        }
        Double cruise = resolved.cruiseSpeedKmh() == null ? null : resolved.cruiseSpeedKmh().value();
        Double range = resolved.estimatedPracticalRangeKm() == null ? null : resolved.estimatedPracticalRangeKm().value();
        WindWaveCapability wind = resolved.windWaveCapability() == null ? null : resolved.windWaveCapability().value();
        return new AnalyzeBoatResponse(
                new BoatCapabilityProposal(cruise, range, wind),
                resolved.warnings(),
                resolved.extractionGaps(),
                resolved.missingHints()
        );
    }
}
