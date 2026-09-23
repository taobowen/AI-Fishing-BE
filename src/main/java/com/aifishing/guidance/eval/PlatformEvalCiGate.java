package com.aifishing.guidance.eval;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.EvalCoverage;
import com.aifishing.guidance.contracts.UsageTelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Blocking PLATFORM_REGRESSION gates: logical budgets and min coverage.
 * Token budget is asserted only when telemetry exists. Wall-clock p50/p95
 * are recorded and never fail CI.
 */
public final class PlatformEvalCiGate {

    private PlatformEvalCiGate() {
    }

    public static EvalLatencySummary assertPlatform(
            GuidanceProperties properties,
            EvalCoverage coverage,
            List<EvalLogicalUsage> cases
    ) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(coverage, "coverage");
        List<EvalLogicalUsage> observed = cases == null ? List.of() : List.copyOf(cases);
        EvalCoverageCalculator.requireMinCoverage(coverage, properties.getEval().getMinCoverage());
        for (int i = 0; i < observed.size(); i++) {
            assertLogicalBudgets(properties, observed.get(i), i);
        }
        return recordLatencies(observed);
    }

    public static void assertLogicalBudgets(GuidanceProperties properties, EvalLogicalUsage usage) {
        assertLogicalBudgets(properties, usage, 0);
    }

    public static EvalLatencySummary recordLatencies(List<EvalLogicalUsage> cases) {
        List<Long> samples = new ArrayList<>();
        if (cases != null) {
            for (EvalLogicalUsage usage : cases) {
                if (usage != null && usage.wallClockMs() != null) {
                    samples.add(usage.wallClockMs());
                }
            }
        }
        return EvalLatencySummary.record(samples);
    }

    private static void assertLogicalBudgets(GuidanceProperties properties, EvalLogicalUsage usage, int index) {
        Objects.requireNonNull(usage, "usage");
        String prefix = "case[" + index + "]";
        if (usage.modelTurns() > properties.getMaxModelTurns()) {
            throw new EvalCiAssertionException(
                    prefix + " modelTurns " + usage.modelTurns()
                            + " exceeded maxModelTurns " + properties.getMaxModelTurns()
            );
        }
        if (usage.toolRounds() > properties.getMaxToolRounds()) {
            throw new EvalCiAssertionException(
                    prefix + " toolRounds " + usage.toolRounds()
                            + " exceeded maxToolRounds " + properties.getMaxToolRounds()
            );
        }
        if (usage.toolCalls() > properties.getMaxToolCalls()) {
            throw new EvalCiAssertionException(
                    prefix + " toolCalls " + usage.toolCalls()
                            + " exceeded maxToolCalls " + properties.getMaxToolCalls()
            );
        }
        assertTokenBudgetIfPresent(properties, usage.telemetry(), prefix);
    }

    static void assertTokenBudgetIfPresent(
            GuidanceProperties properties,
            UsageTelemetry telemetry,
            String prefix
    ) {
        if (telemetry == null || !telemetry.hasTokenTelemetry()) {
            return;
        }
        Integer observed = telemetry.totalTokens();
        if (observed == null && telemetry.inputTokens() != null && telemetry.outputTokens() != null) {
            observed = telemetry.inputTokens() + telemetry.outputTokens();
        }
        if (observed == null) {
            observed = telemetry.inputTokens() != null ? telemetry.inputTokens() : telemetry.outputTokens();
        }
        if (observed == null) {
            return;
        }
        int budget = tokenBudget(properties);
        if (observed > budget) {
            throw new EvalCiAssertionException(
                    prefix + " tokens " + observed + " exceeded token budget " + budget
            );
        }
    }

    static int tokenBudget(GuidanceProperties properties) {
        Integer configured = properties.getEval().getMaxTotalTokens();
        return configured != null ? configured : properties.getMaxContextTokens();
    }
}
