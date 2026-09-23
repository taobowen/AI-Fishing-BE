package com.aifishing.guidance;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.attribution.ComputedAttribution;
import com.aifishing.guidance.attribution.DeliveredSnapshot;
import com.aifishing.guidance.attribution.ObservedUserAction;
import com.aifishing.guidance.attribution.OutcomeAttributionCalculator;
import com.aifishing.guidance.attribution.OutcomeSignal;
import com.aifishing.guidance.attribution.UserActionInference;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.dispatch.DefaultTriggerRouter;
import com.aifishing.guidance.metrics.GuidanceSuccessClassifier;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_1;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase2CombinedRegressionTest {

    private static final Instant T0 = Instant.parse("2026-09-17T16:00:00Z");
    private static final UUID DECISION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FISH = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private GuidanceTriggerOutboxRepository outboxRepository;

    @Test
    void changeRetrieveGotItPlusFishHereThenBiteAndLeaveKeepsOriginalPlanAndDoesNotAccept() {
        DeliveredSnapshot retrieve = new DeliveredSnapshot(DECISION, DECISION, T0, retrieveDecision());
        List<ObservedUserAction> ack = UserActionInference.infer(
                retrieve,
                SessionEventType.ADVICE_ACKNOWLEDGED,
                T0.plusSeconds(5),
                Map.of("sourceEventId", "got-it")
        );
        assertThat(ack).isEmpty();
        assertThat(UserActionInference.feedbackEventType(SessionEventType.ADVICE_ACKNOWLEDGED))
                .isEqualTo(FeedbackStatus.ACKNOWLEDGED)
                .isNotEqualTo(FeedbackStatus.ACCEPTED)
                .isNotEqualTo(FeedbackStatus.REJECTED);

        DefaultTriggerRouter router = new DefaultTriggerRouter(
                new GuidanceProperties(),
                Clock.fixed(T0, ZoneOffset.UTC),
                sessionEventRepository,
                agentRunRepository,
                outboxRepository
        );
        when(sessionEventRepository.findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(SESSION_ID), eq(SessionEventType.BITE), any()
        )).thenReturn(List.of());
        assertThat(router.route(sessionEvent(SessionEventType.USER_STARTED_AD_HOC_FISHING), adHocState()))
                .hasValueSatisfying(decision ->
                        assertThat(decision.primary()).isEqualTo(GuidanceTrigger.USER_STARTED_AD_HOC_FISHING));
        assertThat(router.route(sessionEvent(SessionEventType.ADVICE_ACKNOWLEDGED), adHocState())).isEmpty();
        assertThat(router.route(sessionEvent(SessionEventType.BITE), adHocState())).isEmpty();

        Instant fishOnAt = T0.plusSeconds(20 * 60);
        List<ComputedAttribution> rows = OutcomeAttributionCalculator.compute(
                fishOnAt.plusSeconds(5),
                new GuidanceProperties.Attribution(),
                List.of(retrieve),
                ack,
                List.of(
                        new OutcomeSignal(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), T0.plusSeconds(19 * 60), OutcomeKind.BITE, null),
                        new OutcomeSignal(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), fishOnAt, OutcomeKind.FISH_ON, FISH)
                )
        );
        assertThat(rows).noneMatch(row ->
                GuidanceSuccessClassifier.classify(
                        row.deliveredDecisionId(),
                        row.followedRecommendation(),
                        row.outcomeKind()
                ) == GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(rows).noneMatch(ComputedAttribution::followedRecommendation);

        FishingSessionState state = adHocState();
        assertThat(state.plan().originalPlanSteps()).extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);
        assertThat(state.plan().shortHorizonSteps()).extracting(HorizonStep::tripWaypointId)
                .containsExactly(null, SPOT_3)
                .doesNotContain(SPOT_2);
        assertThat(state.fishing().activityStateSource().name()).isEqualTo("USER_AD_HOC");
        assertThat(new TriggerClippedFishingAgentContextBuilder()
                .build(state, GuidanceTrigger.USER_STARTED_AD_HOC_FISHING, com.aifishing.guidance.contracts.RetrievedMemory.empty())
                .originalPlanSteps()).extracting(OriginalPlanStep::tripWaypointId)
                .containsExactly(SPOT_1, SPOT_2, SPOT_3);
    }

    private static SessionEvent sessionEvent(SessionEventType type) {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                type,
                T0,
                EventSource.CLIENT,
                type.name(),
                Map.of(),
                null
        );
    }

    private static FishingSessionState adHocState() {
        List<OriginalPlanStep> original = List.of(
                new OriginalPlanStep(1, SPOT_1, null, null, null, List.of(), "COMPLETED"),
                new OriginalPlanStep(2, SPOT_2, null, null, null, List.of(), "NAVIGATING"),
                new OriginalPlanStep(3, SPOT_3, null, null, null, List.of(), "UPCOMING")
        );
        List<HorizonStep> horizon = List.of(
                new HorizonStep(1, GuidanceAction.STAY, null, 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, SPOT_3, null, false)
        );
        FishingSessionState base = GuidancePhase2Fixtures.state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90,
                SPOT_2, horizon, original
        );
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                new FishingSessionState.Fishing(
                        SPOT_2,
                        base.fishing().currentSessionWaypointProgressId(),
                        base.fishing().structureType(),
                        base.fishing().depthMinM(),
                        base.fishing().depthMaxM(),
                        base.fishing().lureFamily(),
                        base.fishing().presentation(),
                        base.fishing().retrieveStyle(),
                        base.fishing().timeAtWaypointMinutes(),
                        com.aifishing.guidance.contracts.FishingActivityState.FISHING,
                        T0,
                        com.aifishing.guidance.contracts.ActivityStateSource.USER_AD_HOC,
                        20,
                        20,
                        UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8"),
                        T0,
                        null,
                        null,
                        null
                ),
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );
    }

    private static DeliveredDecision retrieveDecision() {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION,
                GuidanceAction.CHANGE_RETRIEVE,
                null,
                null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("TEST"),
                "Change retrieve",
                List.of(),
                false,
                null,
                0.8
        );
    }
}
