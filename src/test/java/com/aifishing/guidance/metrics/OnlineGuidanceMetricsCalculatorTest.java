package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OnlineGuidanceMetricsCalculatorTest {

    private static final Instant START = Instant.parse("2026-09-16T16:00:00Z");
    private static final Instant END = Instant.parse("2026-09-16T17:00:00Z");
    private static final UUID SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DECISION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID FISH = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void sameFishLocationAndLureCountOncePerDimensionAndRolesCollapse() {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                null,
                List.of(
                        event(DECISION, AttributionDimension.LOCATION, RecommendationRole.PRIMARY, FISH, OutcomeKind.FISH_ON, true),
                        event(DECISION, AttributionDimension.LURE, RecommendationRole.SECONDARY, FISH, OutcomeKind.FISH_ON, true),
                        event(DECISION, AttributionDimension.LOCATION, RecommendationRole.SECONDARY, FISH, OutcomeKind.BITE, true)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(snapshot.metrics().fishOnSuccessCount()).isEqualTo(2);
        assertThat(snapshot.metrics().biteSignalOnlyCount()).isZero();
        assertThat(OnlineGuidanceMetricsCalculator.mergeIdentities(List.of(
                event(DECISION, AttributionDimension.LOCATION, RecommendationRole.PRIMARY, FISH, OutcomeKind.BITE, false),
                event(DECISION, AttributionDimension.LOCATION, RecommendationRole.SECONDARY, FISH, OutcomeKind.FISH_ON, true)
        ))).hasSize(1);
    }

    @Test
    void acceptedWithoutFollowDoesNotEnterSuccessNumeratorAndIsOverride() {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                AttributionDimension.LOCATION,
                List.of(event(DECISION, AttributionDimension.LOCATION, RecommendationRole.PRIMARY, FISH, OutcomeKind.FISH_ON, false)),
                List.of(feedback(DECISION, FeedbackStatus.ACCEPTED)),
                List.of(new FollowObservation(DECISION, AttributionDimension.LOCATION, false, false)),
                List.of(),
                List.of(),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(snapshot.metrics().fishOnSuccessCount()).isZero();
        assertThat(snapshot.metrics().notFollowedCount()).isEqualTo(1);
        assertThat(snapshot.metrics().explicitAcceptanceRate()).isEqualTo(1.0);
        assertThat(snapshot.metrics().observedFollowThroughRate()).isZero();
        assertThat(snapshot.metrics().overrideRate()).isEqualTo(1.0);
        assertThat(snapshot.landing().landedCount()).isZero();
    }

    @Test
    void biteOnlyStaysSeparateFromNoFishAndLandingStaysOutOfSuccessKind() {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                AttributionDimension.LURE,
                List.of(
                        event(DECISION, AttributionDimension.LURE, RecommendationRole.PRIMARY, FISH, OutcomeKind.BITE, true),
                        event(OTHER, AttributionDimension.LURE, RecommendationRole.PRIMARY, null, OutcomeKind.NO_BITE, true),
                        event(DECISION, AttributionDimension.LURE, RecommendationRole.PRIMARY, UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), OutcomeKind.CATCH_LANDED, true)
                ),
                List.of(),
                List.of(
                        new FollowObservation(DECISION, AttributionDimension.LURE, true, false),
                        new FollowObservation(OTHER, AttributionDimension.LURE, true, false)
                ),
                List.of(),
                List.of(),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(snapshot.metrics().biteSignalOnlyCount()).isEqualTo(1);
        assertThat(snapshot.metrics().noFishSignalCount()).isEqualTo(1);
        assertThat(snapshot.metrics().fishOnSuccessCount()).isEqualTo(1);
        assertThat(snapshot.landing().landedCount()).isEqualTo(1);
        assertThat(snapshot.landing().lostCount()).isZero();
    }

    @Test
    void effortNormalizedRatesStayNullWithoutEffortAndDoNotCrossWindows() {
        RecommendationEffortWindow move = new RecommendationEffortWindow(
                SESSION, DECISION, AttributionDimension.LOCATION, START, START.plusSeconds(1800));
        RecommendationEffortWindow retrieve = new RecommendationEffortWindow(
                SESSION, OTHER, AttributionDimension.RETRIEVE, START, START.plusSeconds(600));
        EffortInterval fishing = new EffortInterval(SESSION, START, START.plusSeconds(1800));

        OnlineOutcomeSnapshot location = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                AttributionDimension.LOCATION,
                List.of(event(DECISION, AttributionDimension.LOCATION, RecommendationRole.PRIMARY, FISH, OutcomeKind.FISH_ON, true)),
                List.of(),
                List.of(new FollowObservation(DECISION, AttributionDimension.LOCATION, true, false)),
                List.of(move, retrieve),
                List.of(fishing),
                OnlineOutcomeSafetyCounts.empty()
        );
        OnlineOutcomeSnapshot retrieveSlice = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                AttributionDimension.RETRIEVE,
                List.of(event(OTHER, AttributionDimension.RETRIEVE, RecommendationRole.PRIMARY, FISH, OutcomeKind.BITE, true)),
                List.of(),
                List.of(new FollowObservation(OTHER, AttributionDimension.RETRIEVE, true, false)),
                List.of(move, retrieve),
                List.of(fishing),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(location.metrics().effectiveFishingEffortSeconds()).isEqualTo(1800L);
        assertThat(location.metrics().fishOnPerFishingHourAfterRecommendation()).isCloseTo(2.0, within(0.0001));
        assertThat(retrieveSlice.metrics().effectiveFishingEffortSeconds()).isEqualTo(600L);
        assertThat(retrieveSlice.metrics().bitePerFishingHourAfterRecommendation()).isCloseTo(6.0, within(0.0001));
        assertThat(location.metrics().fishOnSuccessCount()).isEqualTo(1);
        assertThat(retrieveSlice.metrics().fishOnSuccessCount()).isZero();
        assertThat(OnlineGuidanceMetricsCalculator.perFishingHour(1, 0)).isNull();
        assertThat(OnlineGuidanceMetricsCalculator.rate(1, 0)).isNull();
    }

    @Test
    void summingRawCountsThenDerivingDoesNotStoreZeroRates() {
        OnlineOutcomeRawCounts first = new OnlineOutcomeRawCounts(
                1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1800, 0, 0, OnlineOutcomeSafetyCounts.empty());
        OnlineOutcomeRawCounts second = new OnlineOutcomeRawCounts(
                1, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 2, 1, 1800, 1, 0, OnlineOutcomeSafetyCounts.empty());
        OnlineOutcomeRawCounts summed = new OnlineOutcomeRawCounts(
                first.fishOnSuccessCount() + second.fishOnSuccessCount(),
                first.biteSignalOnlyCount() + second.biteSignalOnlyCount(),
                0,
                0,
                0,
                first.followedRecommendationCount() + second.followedRecommendationCount(),
                first.explicitAcceptedCount() + second.explicitAcceptedCount(),
                0,
                first.rejectCount() + second.rejectCount(),
                0,
                first.feedbackCount() + second.feedbackCount(),
                first.followThroughEligibleCount() + second.followThroughEligibleCount(),
                first.overrideEligibleCount() + second.overrideEligibleCount(),
                first.effectiveFishingEffortSeconds() + second.effectiveFishingEffortSeconds(),
                1,
                0,
                OnlineOutcomeSafetyCounts.empty()
        );

        OnlineGuidanceMetrics derived = OnlineGuidanceMetricsCalculator.derive(START, END, AttributionDimension.LOCATION, summed);
        assertThat(derived.fishOnSuccessCount()).isEqualTo(2);
        assertThat(derived.biteSignalOnlyCount()).isEqualTo(1);
        assertThat(derived.explicitAcceptanceRate()).isEqualTo(0.5);
        assertThat(derived.observedFollowThroughRate()).isCloseTo(2.0 / 3.0, within(0.0001));
        assertThat(derived.rejectRate()).isEqualTo(0.5);
        assertThat(derived.fishOnPerFishingHourAfterRecommendation()).isCloseTo(2.0, within(0.0001));
        assertThat(derived.unsafeDeliveredRate()).isNull();
        assertThat(derived.candidateUnsafeRate()).isNull();

        OnlineGuidanceMetrics empty = OnlineGuidanceMetricsCalculator.derive(
                START, END, null, new OnlineOutcomeRawCounts(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, OnlineOutcomeSafetyCounts.empty()));
        assertThat(empty.explicitAcceptanceRate()).isNull();
        assertThat(empty.observedFollowThroughRate()).isNull();
        assertThat(empty.fishOnPerFishingHourAfterRecommendation()).isNull();
        assertThat(empty.bitePerFishingHourAfterRecommendation()).isNull();
    }

    @Test
    void acknowledgedIsNotAcceptedAndUnfollowedAckIsNotOverride() {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                AttributionDimension.LOCATION,
                List.of(event(DECISION, AttributionDimension.LOCATION, RecommendationRole.PRIMARY, FISH, OutcomeKind.FISH_ON, false)),
                List.of(feedback(DECISION, FeedbackStatus.ACKNOWLEDGED)),
                List.of(new FollowObservation(DECISION, AttributionDimension.LOCATION, false, false)),
                List.of(),
                List.of(),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(snapshot.metrics().fishOnSuccessCount()).isZero();
        assertThat(snapshot.metrics().notFollowedCount()).isEqualTo(1);
        assertThat(snapshot.raw().explicitAcceptedCount()).isZero();
        assertThat(snapshot.raw().explicitPartialCount()).isZero();
        assertThat(snapshot.raw().rejectCount()).isZero();
        assertThat(snapshot.metrics().explicitAcceptanceRate()).isZero();
        assertThat(snapshot.metrics().overrideRate()).isZero();
        assertThat(snapshot.raw().overrideCount()).isZero();
    }

    @Test
    void rejectAndUnacceptedOtherActionAreDistinctFromFollowThrough() {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                START,
                END,
                null,
                List.of(),
                List.of(
                        feedback(DECISION, FeedbackStatus.REJECTED),
                        feedback(OTHER, FeedbackStatus.PARTIALLY_FOLLOWED)
                ),
                List.of(
                        new FollowObservation(DECISION, AttributionDimension.LOCATION, false, true),
                        new FollowObservation(OTHER, AttributionDimension.LURE, true, false)
                ),
                List.of(),
                List.of(),
                OnlineOutcomeSafetyCounts.empty()
        );

        assertThat(snapshot.metrics().explicitAcceptanceRate()).isEqualTo(0.5);
        assertThat(snapshot.metrics().rejectRate()).isEqualTo(0.5);
        assertThat(snapshot.metrics().observedFollowThroughRate()).isEqualTo(0.5);
        assertThat(snapshot.metrics().overrideRate()).isEqualTo(0.5);
    }

    private static OnlineOutcomeEvent event(
            UUID decision,
            AttributionDimension dimension,
            RecommendationRole role,
            UUID fish,
            OutcomeKind kind,
            boolean followed
    ) {
        return new OnlineOutcomeEvent(SESSION, decision, dimension, role, fish, kind, followed, START.plusSeconds(60));
    }

    private static FeedbackObservation feedback(UUID decision, FeedbackStatus status) {
        return new FeedbackObservation(SESSION, decision, status, START.plusSeconds(10));
    }
}
