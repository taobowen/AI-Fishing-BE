package com.aifishing.guidance.eval;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.EvalCoverage;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.UsageTelemetry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformEvalCiGateTest {

    @Test
    void logicalBudgetsFailWhenTurnsRoundsOrCallsExceedCaps() {
        GuidanceProperties properties = properties();
        EvalCoverage coverage = EvalCoverage.of(1, 0, 1, 1);

        assertThatThrownBy(() -> PlatformEvalCiGate.assertPlatform(
                properties,
                coverage,
                List.of(new EvalLogicalUsage(9, 1, 1, null, 5L))
        )).isInstanceOf(EvalCiAssertionException.class).hasMessageContaining("maxModelTurns");

        assertThatThrownBy(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(1, 5, 1, null, null)
        )).isInstanceOf(EvalCiAssertionException.class).hasMessageContaining("maxToolRounds");

        assertThatThrownBy(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(1, 1, 13, null, null)
        )).isInstanceOf(EvalCiAssertionException.class).hasMessageContaining("maxToolCalls");
    }

    @Test
    void tokenBudgetIsSkippedWhenTelemetryIsMissingAndFailsOnlyWhenPresent() {
        GuidanceProperties properties = properties();
        UsageTelemetry unknown = new UsageTelemetry(
                GuidanceSchemaVersion.VALUE, "deterministic", "fixture", "1", "prompt-1",
                null, null, null, null, null
        );
        assertThatCode(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(1, 1, 1, unknown, 50_000L)
        )).doesNotThrowAnyException();
        assertThatCode(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(1, 1, 1, null, 50_000L)
        )).doesNotThrowAnyException();

        UsageTelemetry overBudget = new UsageTelemetry(
                GuidanceSchemaVersion.VALUE, "openai", "gpt-4o", "v1", "prompt-1",
                4000, 3000, 7000, null, null
        );
        assertThatThrownBy(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(1, 1, 1, overBudget, 12L)
        )).isInstanceOf(EvalCiAssertionException.class).hasMessageContaining("token budget");

        UsageTelemetry within = new UsageTelemetry(
                GuidanceSchemaVersion.VALUE, "openai", "gpt-4o", "v1", "prompt-1",
                100, 50, 150, null, null
        );
        assertThatCode(() -> PlatformEvalCiGate.assertLogicalBudgets(
                properties,
                new EvalLogicalUsage(2, 1, 3, within, 12L)
        )).doesNotThrowAnyException();
    }

    @Test
    void wallClockPercentilesAreRecordedAndNeverFailCi() {
        EvalCoverage coverage = EvalCoverage.of(1, 0, 1, 1);
        EvalLatencySummary summary = PlatformEvalCiGate.assertPlatform(
                properties(),
                coverage,
                List.of(
                        new EvalLogicalUsage(1, 1, 1, null, 10L),
                        new EvalLogicalUsage(1, 1, 1, null, 20L),
                        new EvalLogicalUsage(1, 1, 1, null, 120_000L)
                )
        );
        assertThat(summary.sampleCount()).isEqualTo(3);
        assertThat(summary.p50Ms()).isEqualTo(20L);
        assertThat(summary.p95Ms()).isEqualTo(120_000L);
    }

    private static GuidanceProperties properties() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setMaxModelTurns(8);
        properties.setMaxToolRounds(4);
        properties.setMaxToolCalls(12);
        properties.setMaxContextTokens(6_000);
        properties.getEval().setMinCoverage(0.8);
        properties.getEval().setMaxTotalTokens(6_000);
        return properties;
    }
}
