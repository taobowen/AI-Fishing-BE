package com.aifishing.guidance.api.admin;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.PlanCreatedBy;
import com.aifishing.guidance.contracts.ReplanScope;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGuidanceSessionReplayIT extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-18T14:00:00Z");

    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private SessionEventRepository sessionEventRepository;
    @Autowired
    private GuidanceTriggerOutboxRepository triggerOutboxRepository;
    @Autowired
    private GuidancePlanVersionRepository planVersionRepository;
    @Autowired
    private GuidancePlanStepRepository planStepRepository;

    @Test
    void unknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/guidance/sessions/" + UUID.randomUUID() + "/timeline")
                        .header("X-User-Id", UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void timelineAndMapTraceDoNotMutateAndKeepPlanLayersDistinct() throws Exception {
        Seed seed = insertReplaySession();
        int sessions = count("fishing_sessions");
        int runs = count("agent_runs");
        int events = count("session_events");
        int outbox = count("guidance_trigger_outbox");
        int horizons = count("guidance_plan_versions");
        int evalRuns = count("guidance_eval_runs");

        mockMvc.perform(asDev(get("/api/v1/admin/guidance/sessions/" + seed.sessionId() + "/timeline")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.sessionId").value(seed.sessionId().toString()))
                .andExpect(jsonPath("$.originalPlan.length()").value(2))
                .andExpect(jsonPath("$.latestShortHorizon.length()").value(1))
                .andExpect(jsonPath("$.latestShortHorizon[0].tripWaypointId").value(seed.wp2().toString()))
                .andExpect(jsonPath("$.originalPlan[0].tripWaypointId").value(seed.wp1().toString()))
                .andExpect(jsonPath("$.events[*].kind", hasItem("USER_STARTED_AD_HOC_FISHING")))
                .andExpect(jsonPath("$.events[*].kind", hasItem("HORIZON_CHANGED")))
                .andExpect(jsonPath("$.events[*].kind", hasItem("AGENT_TRIGGER")))
                .andExpect(jsonPath("$.events[*].kind", hasItem("AGENT_RUN")))
                .andExpect(jsonPath("$.events[*].kind", not(hasItem("GPS_UPDATED"))))
                .andExpect(jsonPath("$.events[?(@.kind == 'AGENT_TRIGGER')].runId").isEmpty())
                .andExpect(jsonPath("$.events[?(@.kind == 'AGENT_TRIGGER')].dispatchStatus", hasItem("PENDING")))
                .andExpect(jsonPath("$.events[?(@.kind == 'AGENT_RUN')].runStatus", hasItem("FALLBACK")))
                .andExpect(jsonPath("$.events[?(@.kind == 'AGENT_RUN')].runStatus", hasItem("FAILED")))
                .andExpect(jsonPath("$.events[?(@.kind == 'AGENT_RUN')].detail.fallbackReason", hasItem(GuidanceFallback.KILL_SWITCH)));

        mockMvc.perform(asDev(get("/api/v1/admin/guidance/sessions/" + seed.sessionId() + "/map-trace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(seed.sessionId().toString()))
                .andExpect(jsonPath("$.originalPlan.features.length()").value(2));

        assertThat(count("fishing_sessions")).isEqualTo(sessions);
        assertThat(count("agent_runs")).isEqualTo(runs);
        assertThat(count("session_events")).isEqualTo(events);
        assertThat(count("guidance_trigger_outbox")).isEqualTo(outbox);
        assertThat(count("guidance_plan_versions")).isEqualTo(horizons);
        assertThat(count("guidance_eval_runs")).isEqualTo(evalRuns);
        entityManager.clear();
        AgentRunEntity unchanged = agentRunRepository.findById(seed.killSwitchRunId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AgentRunStatus.FALLBACK);
        assertThat(unchanged.getFallbackReason()).isEqualTo(GuidanceFallback.KILL_SWITCH);
    }

    private Seed insertReplaySession() {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(T0);
        plan.setPlanningAlgorithmVersion("replay-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = waypoint(plan.getId(), 1, 44.75, -78.92);
        TripWaypoint wp2 = waypoint(plan.getId(), 2, 44.76, -78.91);
        tripWaypointRepository.save(wp1);
        tripWaypointRepository.save(wp2);

        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into fishing_sessions (
                            id, trip_id, user_id, trip_plan_id, started_at, status, total_paused_seconds,
                            activity_state, activity_state_source, created_at
                        ) values (?, ?, ?, ?, now(), 'COMPLETED', 0, 'UNKNOWN', 'UNKNOWN', now())
                        """,
                sessionId,
                trip.getId(),
                DevSeedIds.USER_ID,
                plan.getId()
        );

        sessionEventRepository.saveAndFlush(sessionEvent(sessionId, SessionEventType.GPS_UPDATED, "gps"));
        sessionEventRepository.saveAndFlush(sessionEvent(sessionId, SessionEventType.USER_STARTED_AD_HOC_FISHING, "fish-here"));

        GuidanceTriggerOutboxEntity pending = new GuidanceTriggerOutboxEntity();
        pending.setFishingSessionId(sessionId);
        pending.setPrimaryTrigger(GuidanceTrigger.FISH_ON);
        pending.setRelatedTriggers(List.of());
        pending.setReasonCodes(List.of("FISH_ON"));
        pending.setSource(GuidanceTriggerOutboxSource.EVENT);
        pending.setStatus(GuidanceTriggerOutboxStatus.PENDING);
        pending.setCreatedAt(T0);
        triggerOutboxRepository.saveAndFlush(pending);

        UUID killSwitchRunId = UUID.randomUUID();
        AgentRunEntity kill = new AgentRunEntity();
        kill.setId(killSwitchRunId);
        kill.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        kill.setFishingSessionId(sessionId);
        kill.setTrigger(GuidanceTrigger.USER_REQUEST);
        kill.setStatus(AgentRunStatus.FALLBACK);
        kill.setVisibility(AgentRunVisibility.PRODUCTION);
        kill.setFallbackReason(GuidanceFallback.KILL_SWITCH);
        kill.setStartedAt(T0.plusSeconds(2));
        kill.setFinishedAt(T0.plusSeconds(3));
        agentRunRepository.saveAndFlush(kill);

        AgentRunEntity failed = new AgentRunEntity();
        failed.setId(UUID.randomUUID());
        failed.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        failed.setFishingSessionId(sessionId);
        failed.setTrigger(GuidanceTrigger.FISH_ON);
        failed.setStatus(AgentRunStatus.FAILED);
        failed.setVisibility(AgentRunVisibility.PRODUCTION);
        failed.setFallbackReason(GuidanceFallback.RUN_FAILED);
        failed.setStartedAt(T0.plusSeconds(3));
        failed.setFinishedAt(T0.plusSeconds(4));
        agentRunRepository.saveAndFlush(failed);

        GuidancePlanVersionEntity version = new GuidancePlanVersionEntity();
        version.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        version.setFishingSessionId(sessionId);
        version.setVersion(1);
        version.setReplanReason("FISH_ON");
        version.setReplanScope(ReplanScope.TACTICAL_LOCAL);
        version.setCreatedBy(PlanCreatedBy.AGENT);
        version.setCreatedAt(T0.plusSeconds(4));
        planVersionRepository.saveAndFlush(version);
        GuidancePlanStepEntity step = new GuidancePlanStepEntity();
        step.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        step.setGuidancePlanVersionId(version.getId());
        step.setStep(1);
        step.setType(GuidanceAction.MOVE);
        step.setCommitted(true);
        step.setTripWaypointId(wp2.getId());
        step.setDurationMinutes(15);
        planStepRepository.saveAndFlush(step);

        return new Seed(sessionId, wp1.getId(), wp2.getId(), killSwitchRunId);
    }

    private TripWaypoint waypoint(UUID planId, int sequence, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(planId);
        waypoint.setSequence(sequence);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(lat, lng)));
        waypoint.setReason("replay " + sequence);
        return waypoint;
    }

    private static SessionEventEntity sessionEvent(UUID sessionId, SessionEventType type, String key) {
        SessionEventEntity entity = new SessionEventEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(sessionId);
        entity.setType(type);
        entity.setOccurredAt(T0);
        entity.setPayload(Map.of());
        entity.setSource(EventSource.CLIENT);
        entity.setIdempotencyKey(key);
        return entity;
    }

    private int count(String table) {
        Integer rows = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return rows == null ? 0 : rows;
    }

    private record Seed(UUID sessionId, UUID wp1, UUID wp2, UUID killSwitchRunId) {
    }
}
