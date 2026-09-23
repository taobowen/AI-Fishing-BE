package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal eval run envelope. Scenario fields are placeholders for {@link EvalSuiteKind#SIMULATION}.
 */
public record EvalRun(
        String schemaVersion,
        UUID evalRunId,
        String suite,
        EvalSuiteKind kind,
        ReplayMode replayMode,
        List<RecomputedComponent> recomputedComponents,
        EvalComponentVersions componentVersions,
        String pricingVersion,
        EvalCoverage coverage,
        String scenarioId,
        Long seed,
        String simulationVersion,
        List<String> scenarioTags,
        Instant startedAt,
        Instant finishedAt
) {
    public EvalRun {
        recomputedComponents = List.copyOf(recomputedComponents == null ? List.of() : recomputedComponents);
        scenarioTags = scenarioTags == null ? null : List.copyOf(scenarioTags);
        if (componentVersions == null) {
            componentVersions = EvalComponentVersions.empty();
        }
    }
}
