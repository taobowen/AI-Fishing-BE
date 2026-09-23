package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationEntity;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.persistence.UserActionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventRepository;
import com.aifishing.guidance.replay.AgentRunTraceResponse.ObservedActionEvent;
import com.aifishing.guidance.runtime.GuidanceFallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunTraceLoaderTest {

    private static final Instant T0 = Instant.parse("2026-09-18T14:00:00Z");
    private static final UUID SESSION = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000001");
    private static final UUID RUN = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000020");
    private static final UUID DELIVERED = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000030");
    private static final UUID OUTBOX = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000010");

    private AgentRunRepository runs;
    private AgentToolCallRepository tools;
    private AgentCandidateDecisionRepository candidates;
    private AgentValidationRepository validations;
    private AgentDeliveredDecisionRepository delivered;
    private UserActionEventRepository actions;
    private OutcomeAttributionRepository outcomes;
    private AgentFeedbackRepository feedback;
    private GuidancePlanVersionRepository plans;
    private GuidancePlanStepRepository steps;
    private AgentRunTraceLoader loader;

    @BeforeEach
    void setUp() {
        runs = mock(AgentRunRepository.class);
        tools = mock(AgentToolCallRepository.class);
        candidates = mock(AgentCandidateDecisionRepository.class);
        validations = mock(AgentValidationRepository.class);
        delivered = mock(AgentDeliveredDecisionRepository.class);
        actions = mock(UserActionEventRepository.class);
        outcomes = mock(OutcomeAttributionRepository.class);
        feedback = mock(AgentFeedbackRepository.class);
        plans = mock(GuidancePlanVersionRepository.class);
        steps = mock(GuidancePlanStepRepository.class);
        loader = new AgentRunTraceLoader(
                runs, tools, candidates, validations, delivered, actions, outcomes, feedback, plans, steps
        );
        when(tools.findByRunIdOrderByObservedAtAsc(RUN)).thenReturn(List.of());
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.empty());
        when(validations.findFirstByRunId(RUN)).thenReturn(Optional.empty());
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.empty());
        when(plans.findByFishingSessionIdAndVersion(SESSION, 1)).thenReturn(Optional.empty());
    }

    @Test
    void missingRunIsNotFound() {
        when(runs.findById(RUN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> loader.load(RUN)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void observationHistoryUnknownThenFollowed() {
        AgentRunEntity run = productionRun();
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.of(candidate("STAY")));
        when(validations.findFirstByRunId(RUN)).thenReturn(Optional.of(validation()));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));
        when(actions.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of(
                action(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000041"), T0, false),
                action(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000042"), T0.plusSeconds(30), true)
        ));
        when(outcomes.findByDeliveredDecisionIdOrderByAttributedAtAsc(DELIVERED)).thenReturn(List.of());
        when(feedback.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of());

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.observedAction().history()).extracting(ObservedActionEvent::kind)
                .containsExactly(ObservedActionKind.NOT_FOLLOWED, ObservedActionKind.FOLLOWED);
        assertThat(trace.observedAction().latestKind()).isEqualTo(ObservedActionKind.FOLLOWED);
        assertThat(trace.observedAction().followed()).isTrue();
        assertThat(trace.observedAction().acknowledged()).isFalse();
        assertThat(AgentRunTraceLoader.deriveLatestKind(List.of(), false))
                .isEqualTo(ObservedActionKind.UNKNOWN);
    }

    @Test
    void acknowledgedIsNotFollowed() {
        AgentRunEntity run = productionRun();
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.of(candidate("STAY")));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));
        when(actions.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of());
        when(outcomes.findByDeliveredDecisionIdOrderByAttributedAtAsc(DELIVERED)).thenReturn(List.of());
        AgentFeedbackEntity ack = new AgentFeedbackEntity();
        ack.setId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000051"));
        ack.setDeliveredDecisionId(DELIVERED);
        ack.setStatus(FeedbackStatus.ACKNOWLEDGED);
        ack.setOccurredAt(T0);
        when(feedback.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of(ack));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.observedAction().latestKind()).isEqualTo(ObservedActionKind.ACKNOWLEDGED);
        assertThat(trace.observedAction().followed()).isFalse();
        assertThat(trace.observedAction().acknowledged()).isTrue();
        assertThat(trace.observedAction().history()).isEmpty();
    }

    @Test
    void killSwitchIsFallbackWithNoDelivered() {
        AgentRunEntity run = productionRun();
        run.setStatus(AgentRunStatus.FALLBACK);
        run.setFallbackReason(GuidanceFallback.KILL_SWITCH);
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.status()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(trace.decision()).isNotNull();
        assertThat(trace.decision().fallbackReason()).isEqualTo(GuidanceFallback.KILL_SWITCH);
        assertThat(trace.decision().delivered()).isNull();
        assertThat(trace.decision().deliveredLabeled()).isFalse();
        assertThat(trace.deliveredLabeled()).isFalse();
        assertThat(trace.observedAction().history()).isEmpty();
    }

    @Test
    void shadowNeverLabeledDelivered() {
        AgentRunEntity run = productionRun();
        run.setVisibility(AgentRunVisibility.SHADOW);
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.of(candidate("MOVE")));
        when(validations.findFirstByRunId(RUN)).thenReturn(Optional.of(validation()));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.visibility()).isEqualTo(AgentRunVisibility.SHADOW);
        assertThat(trace.usage().visibility()).isEqualTo(AgentRunVisibility.SHADOW);
        assertThat(trace.deliveredLabeled()).isFalse();
        assertThat(trace.decision().delivered()).isNull();
        assertThat(trace.decision().deliveredLabeled()).isFalse();
        assertThat(trace.decision().candidate()).containsEntry("primaryAction", "MOVE");
    }

    @Test
    void failedBeforeDecisionHasEmptyDecision() {
        AgentRunEntity run = productionRun();
        run.setStatus(AgentRunStatus.FAILED);
        run.setFallbackReason(GuidanceFallback.RUN_FAILED);
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(trace.decision()).isNull();
        assertThat(trace.deliveredLabeled()).isFalse();
    }

    @Test
    void toolsStayInObservedOrderAndNullableVersionsLoad() {
        AgentRunEntity run = productionRun();
        run.setAgentPolicyVersion(null);
        run.setLearningSnapshotVersion(null);
        run.setPromptVersion("guidance-prompt-v1");
        run.setFinishedAt(T0.plusMillis(1500));
        run.setUsageTelemetry(Map.of(
                "schemaVersion", "guidance.contracts.v1",
                "inputTokens", 11,
                "outputTokens", 4,
                "totalTokens", 15
        ));
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.of(candidate("STAY")));
        AgentToolCallEntity first = tool(ToolName.GET_NEARBY_WAYPOINTS, T0);
        AgentToolCallEntity second = tool(ToolName.GET_WAYPOINT_STRUCTURE, T0.plusSeconds(1));
        when(tools.findByRunIdOrderByObservedAtAsc(RUN)).thenReturn(List.of(first, second));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.tools()).extracting(tool -> tool.toolName())
                .containsExactly(ToolName.GET_NEARBY_WAYPOINTS, ToolName.GET_WAYPOINT_STRUCTURE);
        assertThat(trace.versions().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(trace.versions().agentPolicyVersion()).isNull();
        assertThat(trace.versions().learningSnapshotVersion()).isNull();
        assertThat(trace.latencyMs()).isEqualTo(1500L);
        assertThat(trace.triggerOutboxId()).isEqualTo(OUTBOX);
        assertThat(trace.relatedTriggers()).containsExactly(GuidanceTrigger.PLAN_STEP_COMPLETED);
        assertThat(trace.reasonCodes()).containsExactly("FISH_ON");
        assertThat(trace.usage().inputTokens()).isEqualTo(11);
        assertThat(trace.usage().visibility()).isEqualTo(AgentRunVisibility.PRODUCTION);
    }

    @Test
    void outcomeHistoryAndLatest() {
        AgentRunEntity run = productionRun();
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.of(candidate("STAY")));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.of(deliveredRow()));
        when(actions.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of());
        when(feedback.findByDeliveredDecisionIdOrderByOccurredAtAsc(DELIVERED)).thenReturn(List.of());
        OutcomeAttributionEntity first = outcome(
                UUID.fromString("bbbbbbbb-0000-4000-8000-000000000061"), T0, OutcomeKind.BITE);
        OutcomeAttributionEntity second = outcome(
                UUID.fromString("bbbbbbbb-0000-4000-8000-000000000062"), T0.plusSeconds(20), OutcomeKind.FISH_ON);
        when(outcomes.findByDeliveredDecisionIdOrderByAttributedAtAsc(DELIVERED)).thenReturn(List.of(first, second));

        AgentRunTraceResponse trace = loader.load(RUN);

        assertThat(trace.outcome().history()).extracting(event -> event.outcomeKind())
                .containsExactly(OutcomeKind.BITE, OutcomeKind.FISH_ON);
        assertThat(trace.outcome().latest().outcomeKind()).isEqualTo(OutcomeKind.FISH_ON);
    }

    private static AgentRunEntity productionRun() {
        AgentRunEntity run = new AgentRunEntity();
        run.setId(RUN);
        run.setFishingSessionId(SESSION);
        run.setTrigger(GuidanceTrigger.FISH_ON);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setVisibility(AgentRunVisibility.PRODUCTION);
        run.setTriggerOutboxId(OUTBOX);
        run.setRelatedTriggers(List.of(GuidanceTrigger.PLAN_STEP_COMPLETED));
        run.setTriggerReasonCodes(List.of("FISH_ON"));
        run.setStartedAt(T0);
        run.setFinishedAt(T0.plusSeconds(2));
        run.setStateSnapshot(Map.of("schemaVersion", "guidance.contracts.v1"));
        run.setContextSnapshot(Map.of("trigger", "FISH_ON"));
        return run;
    }

    private static AgentCandidateDecisionEntity candidate(String action) {
        AgentCandidateDecisionEntity entity = new AgentCandidateDecisionEntity();
        entity.setRunId(RUN);
        entity.setDecision(Map.of("primaryAction", action));
        return entity;
    }

    private static AgentValidationEntity validation() {
        AgentValidationEntity entity = new AgentValidationEntity();
        entity.setRunId(RUN);
        entity.setResult(Map.of("valid", true));
        return entity;
    }

    private static AgentDeliveredDecisionEntity deliveredRow() {
        AgentDeliveredDecisionEntity entity = new AgentDeliveredDecisionEntity();
        entity.setId(DELIVERED);
        entity.setRunId(RUN);
        entity.setDecision(Map.of("primaryAction", "STAY"));
        return entity;
    }

    private static UserActionEventEntity action(UUID id, Instant at, boolean followed) {
        UserActionEventEntity entity = new UserActionEventEntity();
        entity.setId(id);
        entity.setDeliveredDecisionId(DELIVERED);
        entity.setOccurredAt(at);
        entity.setFollowedPrimary(followed);
        entity.setFollowedRecommendation(followed);
        entity.setRecommendationRole(RecommendationRole.PRIMARY);
        entity.setActualAction(GuidanceAction.STAY);
        entity.setPayload(Map.of());
        return entity;
    }

    private static AgentToolCallEntity tool(ToolName name, Instant at) {
        AgentToolCallEntity entity = new AgentToolCallEntity();
        entity.setRunId(RUN);
        entity.setToolName(name);
        entity.setRequest(Map.of("toolName", name.name()));
        entity.setResult(Map.of("status", "OK"));
        entity.setLatencyMs(12);
        entity.setObservedAt(at);
        return entity;
    }

    private static OutcomeAttributionEntity outcome(UUID id, Instant at, OutcomeKind kind) {
        OutcomeAttributionEntity entity = new OutcomeAttributionEntity();
        entity.setId(id);
        entity.setDeliveredDecisionId(DELIVERED);
        entity.setAttributedAt(at);
        entity.setOutcomeKind(kind);
        entity.setAttributionDimension(AttributionDimension.LOCATION);
        entity.setFollowedRecommendation(true);
        entity.setRecommendationRole(RecommendationRole.PRIMARY);
        return entity;
    }
}
