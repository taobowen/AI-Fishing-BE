package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyRateCountsTest {

    @Test
    void zeroDenominatorStaysNullNotZero() {
        SafetyRates rates = SafetyRateCounts.empty().rates();
        assertThat(rates.candidateUnsafeRate()).isNull();
        assertThat(rates.candidateInvalidWaypointRate()).isNull();
        assertThat(rates.validatorInterceptionRate()).isNull();
        assertThat(rates.unsafeDeliveredRate()).isNull();
        assertThat(rates.invalidDeliveredWaypointRate()).isNull();
    }

    @Test
    void zeroViolationsWithDeliveriesAreExplicitZero() {
        SafetyRateCounts counts = new SafetyRateCounts(4, 4, 0, 0, 0, 0, 0);
        SafetyRates rates = counts.rates();
        assertThat(rates.candidateUnsafeRate()).isZero();
        assertThat(rates.candidateInvalidWaypointRate()).isZero();
        assertThat(rates.validatorInterceptionRate()).isZero();
        assertThat(rates.unsafeDeliveredRate()).isZero();
        assertThat(rates.invalidDeliveredWaypointRate()).isZero();
    }

    @Test
    void recordsCandidateAndDeliveredRatesSeparately() {
        SafetyRateCounts counts = SafetyRateCounts.empty()
                .plusCandidate(invalid(DecisionValidationCheck.WEATHER_UNSAFE))
                .plusCandidate(invalid(DecisionValidationCheck.WAYPOINT_NOT_FOUND))
                .plusCandidate(valid())
                .plusCandidate(invalid(DecisionValidationCheck.SCHEMA_INVALID))
                .plusDelivered(false, false)
                .plusDelivered(false, false)
                .plusDelivered(false, false)
                .plusDelivered(false, false);

        assertThat(counts.candidateCount()).isEqualTo(4);
        assertThat(counts.candidateUnsafeCount()).isEqualTo(1);
        assertThat(counts.candidateInvalidWaypointCount()).isEqualTo(1);
        assertThat(counts.validatorInterceptionCount()).isEqualTo(3);
        assertThat(counts.deliveredCount()).isEqualTo(4);
        assertThat(counts.unsafeDeliveredCount()).isZero();
        assertThat(counts.invalidDeliveredWaypointCount()).isZero();

        SafetyRates rates = counts.rates();
        assertThat(rates.candidateUnsafeRate()).isEqualTo(0.25);
        assertThat(rates.candidateInvalidWaypointRate()).isEqualTo(0.25);
        assertThat(rates.validatorInterceptionRate()).isEqualTo(0.75);
        assertThat(rates.unsafeDeliveredRate()).isZero();
        assertThat(rates.invalidDeliveredWaypointRate()).isZero();
    }

    private static DecisionValidationResult valid() {
        return new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of());
    }

    private static DecisionValidationResult invalid(DecisionValidationCheck check) {
        return new DecisionValidationResult(
                GuidanceSchemaVersion.VALUE,
                false,
                List.of(new ValidationIssue(check, check.name()))
        );
    }
}
