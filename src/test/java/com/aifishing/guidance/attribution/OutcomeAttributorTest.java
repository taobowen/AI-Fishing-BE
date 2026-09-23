package com.aifishing.guidance.attribution;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.metrics.GuidanceSuccessClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeAttributorTest {

    private static final Instant DELIVERED_AT = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID DECISION_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FISH = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID EVENT_ON = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID EVENT_LOST = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    void sameFishOnWritesPrimaryMoveAndSecondaryLureThenLostUpgradesSameFish() {
        DeliveredSnapshot snapshot = snapshot(DECISION_A, moveAndLure(20));
        List<ObservedUserAction> actions = List.of(
                follow(DECISION_A, DELIVERED_AT.plusSeconds(60), GuidanceAction.MOVE, RecommendationRole.PRIMARY, true),
                follow(DECISION_A, DELIVERED_AT.plusSeconds(90), GuidanceAction.CHANGE_LURE, RecommendationRole.SECONDARY, true)
        );
        Instant fishOnAt = DELIVERED_AT.plusSeconds(180);
        List<ComputedAttribution> first = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(10),
                new GuidanceProperties.Attribution(),
                List.of(snapshot),
                actions,
                List.of(new OutcomeSignal(EVENT_ON, fishOnAt, OutcomeKind.FISH_ON, FISH))
        );
        assertThat(first).hasSize(2);
        assertThat(first).allMatch(row -> row.outcomeKind() == OutcomeKind.FISH_ON);
        assertThat(first).allMatch(row -> FISH.equals(row.fishInteractionId()));
        assertThat(first).extracting(ComputedAttribution::attributionDimension)
                .containsExactlyInAnyOrder(AttributionDimension.LOCATION, AttributionDimension.LURE);
        assertThat(first).extracting(ComputedAttribution::recommendationRole)
                .containsExactlyInAnyOrder(RecommendationRole.PRIMARY, RecommendationRole.SECONDARY);

        List<ComputedAttribution> lost = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(30),
                new GuidanceProperties.Attribution(),
                List.of(snapshot),
                actions,
                List.of(
                        new OutcomeSignal(EVENT_ON, fishOnAt, OutcomeKind.FISH_ON, FISH),
                        new OutcomeSignal(EVENT_LOST, fishOnAt.plusSeconds(20), OutcomeKind.CATCH_LOST, FISH)
                )
        );
        assertThat(lost).hasSize(2);
        assertThat(lost).allMatch(row -> row.outcomeKind() == OutcomeKind.CATCH_LOST);
        assertThat(lost).allMatch(row -> AttributionWindows.strategySuccess(row.outcomeKind()));
        assertThat(lost).allMatch(row -> FISH.equals(row.fishInteractionId()));
    }

    @Test
    void laterLureFollowDoesNotInvalidateEarlierMoveWindow() {
        UUID later = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        DeliveredSnapshot move = snapshot(DECISION_A, moveAndLure(20));
        DeliveredSnapshot lureOnly = snapshot(later, lureOnly(15));
        List<ObservedUserAction> actions = List.of(
                follow(DECISION_A, DELIVERED_AT.plusSeconds(60), GuidanceAction.MOVE, RecommendationRole.PRIMARY, true),
                follow(later, DELIVERED_AT.plusSeconds(120), GuidanceAction.CHANGE_LURE, RecommendationRole.PRIMARY, true)
        );
        Instant fishOnAt = DELIVERED_AT.plusSeconds(200);
        List<ComputedAttribution> rows = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(5),
                new GuidanceProperties.Attribution(),
                List.of(move, lureOnly),
                actions,
                List.of(new OutcomeSignal(EVENT_ON, fishOnAt, OutcomeKind.FISH_ON, FISH))
        );
        assertThat(rows).anyMatch(row ->
                row.deliveredDecisionId().equals(DECISION_A)
                        && row.attributionDimension() == AttributionDimension.LOCATION
                        && row.outcomeKind() == OutcomeKind.FISH_ON);
        assertThat(rows).anyMatch(row ->
                row.deliveredDecisionId().equals(later)
                        && row.attributionDimension() == AttributionDimension.LURE
                        && row.outcomeKind() == OutcomeKind.FISH_ON);
    }

    @Test
    void stayFishOnWithoutFollowObservationIsNotSuccess() {
        DeliveredSnapshot stay = snapshot(DECISION_A, stayDecision(20));
        Instant fishOnAt = DELIVERED_AT.plusSeconds(120);
        List<ComputedAttribution> rows = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(5),
                new GuidanceProperties.Attribution(),
                List.of(stay),
                List.of(),
                List.of(new OutcomeSignal(EVENT_ON, fishOnAt, OutcomeKind.FISH_ON, FISH))
        );
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(row -> !row.followedRecommendation());
        assertThat(rows).allMatch(row ->
                GuidanceSuccessClassifier.classify(
                        row.deliveredDecisionId(),
                        row.followedRecommendation(),
                        row.outcomeKind()
                ) == GuidanceSuccessKind.NOT_FOLLOWED);
    }

    @Test
    void stayCapStopsAttributionAfterMinReevaluateAndMaxStay() {
        DeliveredSnapshot stay = snapshot(DECISION_A, stayDecision(45));
        List<ObservedUserAction> actions = List.of(
                follow(DECISION_A, DELIVERED_AT, GuidanceAction.STAY, RecommendationRole.PRIMARY, true)
        );
        Instant inside = DELIVERED_AT.plusSeconds(20 * 60);
        Instant outside = DELIVERED_AT.plusSeconds(31 * 60);
        List<ComputedAttribution> inWindow = OutcomeAttributionCalculator.compute(
                inside.plusSeconds(1),
                new GuidanceProperties.Attribution(),
                List.of(stay),
                actions,
                List.of(new OutcomeSignal(EVENT_ON, inside, OutcomeKind.FISH_ON, FISH))
        );
        List<ComputedAttribution> pastCap = OutcomeAttributionCalculator.compute(
                outside.plusSeconds(1),
                new GuidanceProperties.Attribution(),
                List.of(stay),
                actions,
                List.of(new OutcomeSignal(EVENT_ON, outside, OutcomeKind.FISH_ON, FISH))
        );
        assertThat(inWindow).anyMatch(row -> row.outcomeKind() == OutcomeKind.FISH_ON);
        assertThat(pastCap).noneMatch(row -> row.outcomeKind() == OutcomeKind.FISH_ON);
        assertThat(pastCap).anyMatch(row -> row.outcomeKind() == OutcomeKind.NO_BITE && row.followedRecommendation());
    }

    @Test
    void acknowledgedRetrievePlusFishOnWithoutObservationIsNotConfirmedSuccess() {
        DeliveredSnapshot retrieve = snapshot(DECISION_A, retrieveDecision(20));
        Instant fishOnAt = DELIVERED_AT.plusSeconds(5 * 60);
        List<ObservedUserAction> ackOnly = UserActionInference.infer(
                retrieve,
                com.aifishing.guidance.contracts.SessionEventType.ADVICE_ACKNOWLEDGED,
                DELIVERED_AT.plusSeconds(5),
                Map.of("sourceEventId", "got-it")
        );
        assertThat(ackOnly).isEmpty();
        List<ComputedAttribution> rows = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(5),
                new GuidanceProperties.Attribution(),
                List.of(retrieve),
                ackOnly,
                List.of(new OutcomeSignal(EVENT_ON, fishOnAt, OutcomeKind.FISH_ON, FISH))
        );
        assertThat(rows).noneMatch(ComputedAttribution::followedRecommendation);
        assertThat(rows).noneMatch(row ->
                GuidanceSuccessClassifier.classify(
                        row.deliveredDecisionId(),
                        row.followedRecommendation(),
                        row.outcomeKind()
                ) == GuidanceSuccessKind.FISH_ON_SUCCESS);
    }

    private static ObservedUserAction follow(
            UUID deliveredId,
            Instant at,
            GuidanceAction action,
            RecommendationRole role,
            boolean followed
    ) {
        return new ObservedUserAction(
                deliveredId,
                at,
                action,
                followed,
                role,
                followed && role == RecommendationRole.PRIMARY,
                Map.of("sourceEventId", action.name() + at)
        );
    }

    private static DeliveredSnapshot snapshot(UUID deliveredId, DeliveredDecision decision) {
        return new DeliveredSnapshot(deliveredId, decision.decisionId(), DELIVERED_AT, decision);
    }

    private static DeliveredDecision moveAndLure(int reevaluate) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                reevaluate,
                List.of("TEST"),
                "Move and change lure",
                List.of(),
                false,
                null,
                0.8
        );
    }

    private static DeliveredDecision lureOnly(int reevaluate) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                GuidanceAction.CHANGE_LURE,
                null,
                null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                reevaluate,
                List.of("TEST"),
                "Change lure",
                List.of(),
                false,
                null,
                0.8
        );
    }

    private static DeliveredDecision stayDecision(int reevaluate) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.STAY,
                null,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                reevaluate,
                List.of("TEST"),
                "Stay",
                List.of(),
                false,
                null,
                0.8
        );
    }

    private static DeliveredDecision retrieveDecision(int reevaluate) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.CHANGE_RETRIEVE,
                null,
                null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                reevaluate,
                List.of("TEST"),
                "Change retrieve",
                List.of(),
                false,
                null,
                0.8
        );
    }
}
