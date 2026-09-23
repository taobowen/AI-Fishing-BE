package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.ReflectionCauseKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID DECISION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void threeRejectsArePreferenceConflictNotConsecutiveFailure() {
        List<ReflectionEvaluation.FeedbackObservation> rejects = List.of(
                reject(1, GuidanceRejectReason.TOO_FAR),
                reject(2, GuidanceRejectReason.TOO_FAR),
                reject(3, GuidanceRejectReason.TOO_FAR)
        );
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(180),
                new GuidanceProperties.Attribution(),
                List.of(),
                rejects,
                List.of()
        );
        assertThat(result.consecutiveFailure()).isFalse();
        assertThat(result.reflections())
                .allMatch(row -> row.causeKind() == ReflectionCauseKind.USER_PREFERENCE_CONFLICT);
        assertThat(result.reflections())
                .noneMatch(row -> row.causeKind() == ReflectionCauseKind.STRATEGY_FAILURE);
    }

    @Test
    void threeFollowedNoFishOnWindowsEmitStrategyFailure() {
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(3600),
                new GuidanceProperties.Attribution(),
                List.of(
                        noBite(1),
                        noBite(2),
                        noBite(3)
                ),
                List.of(),
                List.of()
        );
        assertThat(result.consecutiveFailure()).isTrue();
        assertThat(result.reflections())
                .anyMatch(row -> row.causeKind() == ReflectionCauseKind.STRATEGY_FAILURE);
    }

    @Test
    void acknowledgedIsNotRejectedAndResetsRejectStreak() {
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(180),
                new GuidanceProperties.Attribution(),
                List.of(),
                List.of(
                        reject(1, GuidanceRejectReason.TOO_FAR),
                        ack(2),
                        reject(3, GuidanceRejectReason.TOO_FAR)
                ),
                List.of()
        );
        assertThat(result.reflections())
                .noneMatch(row -> row.text() != null && row.text().startsWith("Repeated "));
        assertThat(result.reflections())
                .noneMatch(row -> row.text() != null && row.text().contains("ACKNOWLEDGED"));
    }

    @Test
    void absenceOfFeedbackIsNotRejection() {
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(180),
                new GuidanceProperties.Attribution(),
                List.of(),
                List.of(),
                List.of()
        );
        assertThat(result.reflections()).isEmpty();
        assertThat(result.consecutiveFailure()).isFalse();
    }

    @Test
    void repeatedAcknowledgedDoesNotTriggerRejectReflection() {
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(180),
                new GuidanceProperties.Attribution(),
                List.of(),
                List.of(ack(1), ack(2), ack(3)),
                List.of()
        );
        assertThat(result.reflections()).isEmpty();
        assertThat(result.consecutiveFailure()).isFalse();
    }

    @Test
    void fishOnThenLostResetsFailureStreak() {
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                T0.plusSeconds(3600),
                new GuidanceProperties.Attribution(),
                List.of(
                        noBite(1),
                        noBite(2),
                        successLost(3),
                        noBite(4),
                        noBite(5)
                ),
                List.of(),
                List.of()
        );
        assertThat(result.consecutiveFailure()).isFalse();
        assertThat(result.reflections())
                .noneMatch(row -> row.causeKind() == ReflectionCauseKind.STRATEGY_FAILURE);
    }

    private static ReflectionEvaluation.FeedbackObservation reject(int index, GuidanceRejectReason reason) {
        return new ReflectionEvaluation.FeedbackObservation(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + index),
                DECISION,
                FeedbackStatus.REJECTED,
                reason,
                T0.plusSeconds(index * 60L)
        );
    }

    private static ReflectionEvaluation.FeedbackObservation ack(int index) {
        return new ReflectionEvaluation.FeedbackObservation(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + index),
                DECISION,
                FeedbackStatus.ACKNOWLEDGED,
                null,
                T0.plusSeconds(index * 60L)
        );
    }

    private static ComputedAttribution noBite(int index) {
        return new ComputedAttribution(
                UUID.fromString("10000000-0000-0000-0000-00000000000" + index),
                null,
                DECISION,
                AttributionDimension.LOCATION,
                true,
                RecommendationRole.PRIMARY,
                OutcomeKind.NO_BITE,
                AttributionWindowKind.STAY,
                0.3,
                T0.plusSeconds(index * 600L)
        );
    }

    private static ComputedAttribution successLost(int index) {
        return new ComputedAttribution(
                UUID.fromString("20000000-0000-0000-0000-00000000000" + index),
                UUID.fromString("30000000-0000-0000-0000-00000000000" + index),
                DECISION,
                AttributionDimension.LOCATION,
                true,
                RecommendationRole.PRIMARY,
                OutcomeKind.CATCH_LOST,
                AttributionWindowKind.STAY,
                0.8,
                T0.plusSeconds(index * 600L)
        );
    }
}
