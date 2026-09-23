package com.aifishing.guidance.replay;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.PlanCreatedBy;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventEntity;
import com.aifishing.guidance.runtime.GuidanceFallback;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineAssemblerTest {

    private static final Instant T0 = Instant.parse("2026-09-18T14:00:00Z");
    private static final UUID SESSION = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID OUTBOX = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000010");
    private static final UUID RUN = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000020");
    private static final UUID SHADOW = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000021");
    private static final UUID UNRELATED = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000022");

    @Test
    void ordersSameTimestampBySourcePriorityThenId() {
        UUID eventId = UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000aa");
        SessionEventEntity sessionEvent = sessionEvent(eventId, SessionEventType.BITE, T0);
        GuidanceTriggerOutboxEntity trigger = outbox(OUTBOX, T0, List.of());
        AgentRunEntity run = run(RUN, OUTBOX, AgentRunVisibility.PRODUCTION, T0);

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(sessionEvent),
                List.of(trigger),
                List.of(run),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(events).extracting(SessionTimelineEvent::source)
                .containsExactly(TimelineSource.SESSION_EVENT, TimelineSource.TRIGGER, TimelineSource.AGENT_RUN);
        assertThat(events).extracting(SessionTimelineEvent::kind)
                .containsExactly("BITE", "AGENT_TRIGGER", "AGENT_RUN");
    }

    @Test
    void excludesGpsUpdated() {
        SessionEventEntity gps = sessionEvent(UUID.randomUUID(), SessionEventType.GPS_UPDATED, T0);
        SessionEventEntity bite = sessionEvent(UUID.randomUUID(), SessionEventType.BITE, T0.plusSeconds(1));

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(gps, bite), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(events).extracting(SessionTimelineEvent::kind).containsExactly("BITE");
    }

    @Test
    void triggerRunIdUsesOutboxFkNotTimestamp() {
        GuidanceTriggerOutboxEntity unexecuted = outbox(OUTBOX, T0, List.of());
        AgentRunEntity nearby = run(UNRELATED, null, AgentRunVisibility.PRODUCTION, T0);
        AgentRunEntity matched = run(RUN, OUTBOX, AgentRunVisibility.PRODUCTION, T0.plusSeconds(30));
        AgentRunEntity shadow = run(SHADOW, OUTBOX, AgentRunVisibility.SHADOW, T0.plusSeconds(1));

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(),
                List.of(unexecuted),
                List.of(nearby, matched, shadow),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        SessionTimelineEvent trigger = events.stream()
                .filter(event -> event.kind().equals("AGENT_TRIGGER"))
                .findFirst()
                .orElseThrow();
        assertThat(trigger.runId()).isEqualTo(RUN);
        assertThat(trigger.dispatchStatus()).isEqualTo(TriggerDispatchStatus.PENDING);

        SessionTimelineEvent unmatchedTrigger = TimelineAssembler.assemble(
                List.of(),
                List.of(outbox(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000099"), T0, List.of())),
                List.of(nearby),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).stream().filter(event -> event.kind().equals("AGENT_TRIGGER")).findFirst().orElseThrow();
        assertThat(unmatchedTrigger.runId()).isNull();
    }

    @Test
    void coalescedWhenRelatedTriggersMerged() {
        GuidanceTriggerOutboxEntity merged = outbox(OUTBOX, T0, List.of(GuidanceTrigger.PLAN_STEP_COMPLETED));
        merged.setStatus(GuidanceTriggerOutboxStatus.DONE);

        SessionTimelineEvent trigger = TimelineAssembler.assemble(
                List.of(), List.of(merged), List.of(), List.of(), List.of(), List.of(), List.of()
        ).getFirst();

        assertThat(trigger.dispatchStatus()).isEqualTo(TriggerDispatchStatus.COALESCED);
        assertThat(trigger.detail().get("outboxStatus")).isEqualTo("DONE");
    }

    @Test
    void ordersAllSourcesAtSameTimestampByPriorityThenId() {
        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(
                        sessionEvent(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000a1"), SessionEventType.BITE, T0),
                        sessionEvent(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000a2"), SessionEventType.ADVICE_CREATED, T0)
                ),
                List.of(outbox(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000b1"), T0, List.of())),
                List.of(run(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000c1"), null, AgentRunVisibility.PRODUCTION, T0)),
                List.of(observed(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000d1"), T0)),
                List.of(outcome(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000e1"), T0)),
                List.of(horizon(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000f1"), T0)),
                List.of(catchEvent(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000aa"), T0, CatchOutcome.LANDED))
        );

        assertThat(events).extracting(SessionTimelineEvent::source).containsExactly(
                TimelineSource.SESSION_EVENT,
                TimelineSource.TRIGGER,
                TimelineSource.AGENT_RUN,
                TimelineSource.ADVICE,
                TimelineSource.OBSERVED_ACTION,
                TimelineSource.OUTCOME,
                TimelineSource.HORIZON,
                TimelineSource.CATCH
        );
        assertThat(events).extracting(SessionTimelineEvent::kind).containsExactly(
                "BITE",
                "AGENT_TRIGGER",
                "AGENT_RUN",
                "ADVICE_CREATED",
                "OBSERVED_ACTION",
                "OUTCOME",
                "HORIZON_CHANGED",
                "LANDED"
        );
    }

    @Test
    void fishHereAppearsAndGpsUpdatedDoesNot() {
        SessionEventEntity fishHere = sessionEvent(
                UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000b2"),
                SessionEventType.USER_STARTED_AD_HOC_FISHING,
                T0
        );
        SessionEventEntity gps = sessionEvent(UUID.randomUUID(), SessionEventType.GPS_UPDATED, T0);

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(gps, fishHere), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(events).extracting(SessionTimelineEvent::kind).containsExactly("USER_STARTED_AD_HOC_FISHING");
        assertThat(events.getFirst().source()).isEqualTo(TimelineSource.SESSION_EVENT);
    }

    @Test
    void unexecutedTriggerIsNeverAnAgentRun() {
        GuidanceTriggerOutboxEntity pending = outbox(OUTBOX, T0, List.of());

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(), List.of(pending), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().kind()).isEqualTo("AGENT_TRIGGER");
        assertThat(events.getFirst().runId()).isNull();
        assertThat(events.getFirst().dispatchStatus()).isEqualTo(TriggerDispatchStatus.PENDING);
        assertThat(events).noneMatch(event -> event.kind().equals("AGENT_RUN"));
    }

    @Test
    void killSwitchAndFailedRunsStayVisible() {
        AgentRunEntity kill = run(RUN, null, AgentRunVisibility.PRODUCTION, T0);
        kill.setStatus(AgentRunStatus.FALLBACK);
        kill.setFallbackReason(GuidanceFallback.KILL_SWITCH);
        AgentRunEntity failed = run(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000023"), null, AgentRunVisibility.PRODUCTION, T0.plusSeconds(1));
        failed.setStatus(AgentRunStatus.FAILED);
        failed.setFallbackReason(GuidanceFallback.RUN_FAILED);

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(), List.of(), List.of(kill, failed), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(events).extracting(SessionTimelineEvent::runStatus)
                .containsExactly(AgentRunStatus.FALLBACK, AgentRunStatus.FAILED);
        assertThat(events.getFirst().detail().get("fallbackReason")).isEqualTo(GuidanceFallback.KILL_SWITCH);
        assertThat(events.getLast().detail().get("fallbackReason")).isEqualTo(GuidanceFallback.RUN_FAILED);
    }

    @Test
    void horizonChangedIsSeparateFromSessionEvents() {
        GuidancePlanVersionEntity version = horizon(UUID.fromString("aaaaaaaa-0000-4000-8000-0000000000f2"), T0);

        List<SessionTimelineEvent> events = TimelineAssembler.assemble(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(version), List.of()
        );

        assertThat(events).extracting(SessionTimelineEvent::kind).containsExactly("HORIZON_CHANGED");
        assertThat(events.getFirst().source()).isEqualTo(TimelineSource.HORIZON);
        assertThat(events.getFirst().detail().get("version")).isEqualTo(2);
    }

    @Test
    void shadowRunIsLabeledAndNeverLinkedAsProductionTriggerRun() {
        AgentRunEntity shadow = run(SHADOW, null, AgentRunVisibility.SHADOW, T0);
        shadow.setStatus(AgentRunStatus.COMPLETED);

        SessionTimelineEvent event = TimelineAssembler.assemble(
                List.of(), List.of(), List.of(shadow), List.of(), List.of(), List.of(), List.of()
        ).getFirst();

        assertThat(event.visibility()).isEqualTo(AgentRunVisibility.SHADOW);
        assertThat(event.kind()).isEqualTo("AGENT_RUN");
        assertThat(event.detail().get("visibility")).isEqualTo("SHADOW");
    }

    private static SessionEventEntity sessionEvent(UUID id, SessionEventType type, Instant at) {
        SessionEventEntity entity = new SessionEventEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setType(type);
        entity.setOccurredAt(at);
        entity.setSource(EventSource.CLIENT);
        entity.setPayload(Map.of());
        return entity;
    }

    private static GuidanceTriggerOutboxEntity outbox(UUID id, Instant at, List<GuidanceTrigger> related) {
        GuidanceTriggerOutboxEntity entity = new GuidanceTriggerOutboxEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setPrimaryTrigger(GuidanceTrigger.FISH_ON);
        entity.setRelatedTriggers(related);
        entity.setReasonCodes(List.of("FISH_ON"));
        entity.setSource(GuidanceTriggerOutboxSource.EVENT);
        entity.setStatus(GuidanceTriggerOutboxStatus.PENDING);
        entity.setCreatedAt(at);
        return entity;
    }

    private static AgentRunEntity run(UUID id, UUID outboxId, AgentRunVisibility visibility, Instant startedAt) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setTrigger(GuidanceTrigger.FISH_ON);
        entity.setStatus(AgentRunStatus.COMPLETED);
        entity.setVisibility(visibility);
        entity.setTriggerOutboxId(outboxId);
        entity.setStartedAt(startedAt);
        return entity;
    }

    private static UserActionEventEntity observed(UUID id, Instant at) {
        UserActionEventEntity entity = new UserActionEventEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setOccurredAt(at);
        entity.setFollowedPrimary(false);
        entity.setPayload(Map.of());
        return entity;
    }

    private static OutcomeAttributionEntity outcome(UUID id, Instant at) {
        OutcomeAttributionEntity entity = new OutcomeAttributionEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setAttributedAt(at);
        return entity;
    }

    private static GuidancePlanVersionEntity horizon(UUID id, Instant at) {
        GuidancePlanVersionEntity entity = new GuidancePlanVersionEntity();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setVersion(2);
        entity.setParentVersion(1);
        entity.setReplanReason("FISH_ON");
        entity.setCreatedBy(PlanCreatedBy.AGENT);
        entity.setCreatedAt(at);
        return entity;
    }

    private static CatchEvent catchEvent(UUID id, Instant at, CatchOutcome outcome) {
        CatchEvent entity = new CatchEvent();
        entity.setId(id);
        entity.setFishingSessionId(SESSION);
        entity.setOccurredAt(at);
        entity.setOutcome(outcome);
        entity.setStatus(CatchStatus.ACTIVE);
        return entity;
    }
}
