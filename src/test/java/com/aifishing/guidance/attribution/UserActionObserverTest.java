package com.aifishing.guidance.attribution;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SessionEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserActionObserverTest {

    private static final Instant AT = Instant.parse("2026-09-16T14:05:00Z");
    private static final UUID DELIVERED = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void secondaryLureFollowIsIndependentOfPrimaryMove() {
        DeliveredSnapshot snapshot = snapshot(moveAndLure());
        List<ObservedUserAction> arrival = UserActionInference.infer(
                snapshot,
                SessionEventType.WAYPOINT_ENTERED,
                AT,
                Map.of("waypointId", GuidancePhase2Fixtures.TRIP_WAYPOINT.toString(), "sourceEventId", "arrive")
        );
        List<ObservedUserAction> lure = UserActionInference.infer(
                snapshot,
                SessionEventType.LURE_CHANGED,
                AT.plusSeconds(60),
                Map.of("lureFamily", LureFamily.TUBE.name(), "sourceEventId", "lure")
        );

        assertThat(arrival).singleElement().satisfies(action -> {
            assertThat(action.recommendationRole()).isEqualTo(RecommendationRole.PRIMARY);
            assertThat(action.actualAction()).isEqualTo(GuidanceAction.MOVE);
            assertThat(action.followedRecommendation()).isTrue();
            assertThat(action.followedPrimary()).isTrue();
        });
        assertThat(lure).singleElement().satisfies(action -> {
            assertThat(action.recommendationRole()).isEqualTo(RecommendationRole.SECONDARY);
            assertThat(action.actualAction()).isEqualTo(GuidanceAction.CHANGE_LURE);
            assertThat(action.followedRecommendation()).isTrue();
            assertThat(action.followedPrimary()).isFalse();
        });
    }

    @Test
    void rejectMarksEveryRecommendedDimensionUnfollowed() {
        List<ObservedUserAction> rejected = UserActionInference.infer(
                snapshot(moveAndLure()),
                SessionEventType.ADVICE_REJECTED,
                AT,
                Map.of("sourceEventId", "reject", "rejectReason", "TOO_FAR")
        );
        assertThat(rejected).hasSize(2);
        assertThat(rejected).allMatch(action -> !action.followedRecommendation());
    }

    @Test
    void acknowledgedDoesNotSetFollowedEvenOnWaypointStay() {
        Map<String, Object> onWaypoint = Map.of("onWaypoint", Boolean.TRUE, "sourceEventId", "ack");
        assertThat(UserActionInference.infer(
                snapshot(stay()),
                SessionEventType.ADVICE_ACKNOWLEDGED,
                AT,
                onWaypoint
        )).isEmpty();
        assertThat(UserActionInference.infer(
                snapshot(moveAndLure()),
                SessionEventType.ADVICE_ACKNOWLEDGED,
                AT,
                onWaypoint
        )).isEmpty();
        assertThat(UserActionInference.feedbackEventType(SessionEventType.ADVICE_ACKNOWLEDGED))
                .isEqualTo(FeedbackStatus.ACKNOWLEDGED);
    }

    @Test
    void historicalAcceptedAndRejectedEventsStillReplayFollowInference() throws Exception {
        com.aifishing.guidance.contracts.SessionEvent accepted =
                com.aifishing.guidance.contracts.GuidanceContracts.mapper().readValue(
                        """
                        {"schemaVersion":"guidance.contracts.v1","type":"ADVICE_ACCEPTED","occurredAt":"2026-09-16T14:05:00Z","source":"CLIENT","idempotencyKey":"old-accept","payload":{"onWaypoint":true,"sourceEventId":"accept"}}
                        """,
                        com.aifishing.guidance.contracts.SessionEvent.class
                );
        List<ObservedUserAction> replayed = UserActionInference.infer(
                snapshot(stay()),
                accepted.type(),
                accepted.occurredAt(),
                accepted.payload()
        );
        assertThat(replayed).singleElement().satisfies(action -> {
            assertThat(action.actualAction()).isEqualTo(GuidanceAction.STAY);
            assertThat(action.followedRecommendation()).isTrue();
        });

        com.aifishing.guidance.contracts.SessionEvent rejected =
                com.aifishing.guidance.contracts.GuidanceContracts.mapper().readValue(
                        """
                        {"schemaVersion":"guidance.contracts.v1","type":"ADVICE_REJECTED","occurredAt":"2026-09-16T14:06:00Z","source":"CLIENT","idempotencyKey":"old-reject","payload":{"sourceEventId":"reject","rejectReason":"TOO_FAR"}}
                        """,
                        com.aifishing.guidance.contracts.SessionEvent.class
                );
        List<ObservedUserAction> replayReject = UserActionInference.infer(
                snapshot(moveAndLure()),
                rejected.type(),
                rejected.occurredAt(),
                rejected.payload()
        );
        assertThat(replayReject).hasSize(2);
        assertThat(replayReject).allMatch(action -> !action.followedRecommendation());
    }

    @Test
    void acceptedOnWaypointStillInfersStayFollow() {
        List<ObservedUserAction> accepted = UserActionInference.infer(
                snapshot(stay()),
                SessionEventType.ADVICE_ACCEPTED,
                AT,
                Map.of("onWaypoint", Boolean.TRUE, "sourceEventId", "accept")
        );
        assertThat(accepted).singleElement().satisfies(action -> {
            assertThat(action.actualAction()).isEqualTo(GuidanceAction.STAY);
            assertThat(action.followedRecommendation()).isTrue();
        });
    }

    @Test
    void changeLureFollowsFromLureChangeNotAck() {
        DeliveredSnapshot snapshot = snapshot(moveAndLure());
        assertThat(UserActionInference.infer(
                snapshot,
                SessionEventType.ADVICE_ACKNOWLEDGED,
                AT,
                Map.of("onWaypoint", Boolean.TRUE, "sourceEventId", "ack")
        )).isEmpty();
        List<ObservedUserAction> lure = UserActionInference.infer(
                snapshot,
                SessionEventType.LURE_CHANGED,
                AT.plusSeconds(30),
                Map.of("lureFamily", LureFamily.TUBE.name(), "sourceEventId", "lure")
        );
        assertThat(lure).singleElement().satisfies(action -> {
            assertThat(action.actualAction()).isEqualTo(GuidanceAction.CHANGE_LURE);
            assertThat(action.followedRecommendation()).isTrue();
        });
    }

    @Test
    void depthAndRetrieveStayUnknownWithoutPresentationEvent() {
        DeliveredSnapshot snapshot = snapshot(depthAndRetrieve());
        assertThat(UserActionInference.infer(
                snapshot,
                SessionEventType.ADVICE_ACKNOWLEDGED,
                AT,
                Map.of("onWaypoint", Boolean.TRUE, "sourceEventId", "ack")
        )).isEmpty();
        assertThat(UserActionInference.infer(
                snapshot,
                SessionEventType.ADVICE_ACCEPTED,
                AT,
                Map.of("onWaypoint", Boolean.TRUE, "sourceEventId", "accept")
        )).isEmpty();
    }

    private static DeliveredSnapshot snapshot(DeliveredDecision decision) {
        return new DeliveredSnapshot(DELIVERED, decision.decisionId(), AT.minusSeconds(60), decision);
    }

    private static DeliveredDecision moveAndLure() {
        return decision(GuidanceAction.MOVE, GuidanceAction.CHANGE_LURE, "Move and change lure");
    }

    private static DeliveredDecision stay() {
        return decision(GuidanceAction.STAY, null, "Stay");
    }

    private static DeliveredDecision depthAndRetrieve() {
        return decision(GuidanceAction.CHANGE_DEPTH, GuidanceAction.CHANGE_RETRIEVE, "Change depth and retrieve");
    }

    private static DeliveredDecision decision(GuidanceAction primary, GuidanceAction secondary, String explanation) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                primary,
                secondary,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("TEST"),
                explanation,
                List.of(),
                false,
                null,
                0.8
        );
    }
}
