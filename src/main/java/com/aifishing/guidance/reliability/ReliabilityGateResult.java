package com.aifishing.guidance.reliability;

import java.util.List;

public record ReliabilityGateResult(
        boolean passed,
        boolean hardGatePassed,
        List<String> blockingFailures,
        List<String> qualitySignals
) {
    public ReliabilityGateResult {
        blockingFailures = List.copyOf(blockingFailures == null ? List.of() : blockingFailures);
        qualitySignals = List.copyOf(qualitySignals == null ? List.of() : qualitySignals);
    }
}
