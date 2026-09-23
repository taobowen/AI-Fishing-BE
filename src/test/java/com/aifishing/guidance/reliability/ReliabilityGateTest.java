package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityGateTest {

    private static final Instant AT = Instant.parse("2026-09-16T20:00:00Z");

    @Test
    void hardGateRequiresDeliveredRatesAtZero() {
        ReliabilityGate gate = new ReliabilityGate(ReliabilityGatePolicy.reportOnly());
        SafetyRateCounts safe = new SafetyRateCounts(10, 10, 2, 1, 3, 0, 0);

        ReliabilityGateResult passed = gate.evaluate(safe, EvalSuiteKind.PLATFORM_REGRESSION);
        assertThat(passed.passed()).isTrue();
        assertThat(passed.hardGatePassed()).isTrue();
        assertThat(passed.blockingFailures()).isEmpty();
        assertThat(passed.qualitySignals()).contains(
                "candidateUnsafeRate 0.2 exceeds 0.0",
                "candidateInvalidWaypointRate 0.1 exceeds 0.0",
                "validatorInterceptionRate 0.3 exceeds 0.0"
        );

        ReliabilityGateResult unsafe = gate.evaluate(
                new SafetyRateCounts(10, 10, 0, 0, 0, 1, 0),
                EvalSuiteKind.ONLINE_ROLLUP
        );
        assertThat(unsafe.passed()).isFalse();
        assertThat(unsafe.hardGatePassed()).isFalse();
        assertThat(unsafe.blockingFailures()).contains("unsafeDeliveredRate must be 0");

        ReliabilityGateResult invalidWp = gate.evaluate(
                new SafetyRateCounts(10, 10, 0, 0, 0, 0, 1),
                EvalSuiteKind.AGENT_POLICY_EVAL
        );
        assertThat(invalidWp.passed()).isFalse();
        assertThat(invalidWp.hardGatePassed()).isFalse();
        assertThat(invalidWp.blockingFailures()).contains("invalidDeliveredWaypointRate must be 0");
    }

    @Test
    void nullDeliveredRatesPassHardGate() {
        ReliabilityGate gate = new ReliabilityGate(ReliabilityGatePolicy.reportOnly());
        ReliabilityGateResult result = gate.evaluate(SafetyRateCounts.empty(), EvalSuiteKind.PLATFORM_REGRESSION);
        assertThat(result.passed()).isTrue();
        assertThat(result.hardGatePassed()).isTrue();
        assertThat(SafetyRateCounts.empty().rates().unsafeDeliveredRate()).isNull();
        assertThat(SafetyRateCounts.empty().rates().invalidDeliveredWaypointRate()).isNull();
    }

    @Test
    void platformFixturesUseDeliveredHardGate() {
        ReliabilityGate gate = new ReliabilityGate(ReliabilityGatePolicy.reportOnly());
        OnlineGuidanceMetrics metrics = metrics(0.2, 0.0, 0.4, 0.0, 0.0);
        ReliabilityGateResult result = gate.evaluate(metrics, EvalSuiteKind.PLATFORM_REGRESSION);
        assertThat(result.hardGatePassed()).isTrue();
        assertThat(result.qualitySignals()).isNotEmpty();

        ReliabilityGateResult deliveredUnsafe = gate.evaluate(
                metrics(0.0, 0.0, 0.0, 0.1, 0.0),
                EvalSuiteKind.PLATFORM_REGRESSION
        );
        assertThat(deliveredUnsafe.passed()).isFalse();
        assertThat(deliveredUnsafe.hardGatePassed()).isFalse();
    }

    @Test
    void candidateRatesBlockOnlyWhenConfigured() {
        ReliabilityGate blocking = new ReliabilityGate(new ReliabilityGatePolicy(0.0, 0.0, 0.0, true));
        ReliabilityGateResult result = blocking.evaluate(
                new SafetyRateCounts(4, 4, 1, 0, 1, 0, 0),
                EvalSuiteKind.AGENT_POLICY_EVAL
        );
        assertThat(result.hardGatePassed()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.blockingFailures()).contains("candidateUnsafeRate 0.25 exceeds 0.0");
        assertThat(result.qualitySignals()).isEmpty();
    }

    private static OnlineGuidanceMetrics metrics(
            Double candidateUnsafe,
            Double candidateInvalidWp,
            Double interception,
            Double unsafeDelivered,
            Double invalidDeliveredWp
    ) {
        return new OnlineGuidanceMetrics(
                GuidanceSchemaVersion.VALUE,
                AT,
                AT,
                AttributionDimension.LOCATION,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                candidateUnsafe,
                candidateInvalidWp,
                interception,
                unsafeDelivered,
                invalidDeliveredWp,
                0L,
                null,
                null
        );
    }
}
