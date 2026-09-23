package com.aifishing.guidance.api.admin;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.eval.EvalFixtures;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGuidanceRunReplayIT extends AbstractIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Test
    void evaluateWritesEvalRowsOnlyAndGetDoesNotMutate() throws Exception {
        UUID sessionId = insertSession();
        UUID runId = insertRun(sessionId);

        int sessions = count("fishing_sessions");
        int runs = count("agent_runs");
        int actions = count("user_action_events");
        int outcomes = count("outcome_attributions");
        int delivered = count("agent_delivered_decisions");
        int evalRuns = count("guidance_eval_runs");

        mockMvc.perform(asDev(get("/api/v1/admin/guidance/runs/" + runId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.visibility").value("PRODUCTION"))
                .andExpect(jsonPath("$.deliveredLabeled").value(false))
                .andExpect(jsonPath("$.observedAction.latestKind").value("UNKNOWN"))
                .andExpect(jsonPath("$.observedAction.followed").value(false));

        assertThat(count("fishing_sessions")).isEqualTo(sessions);
        assertThat(count("agent_runs")).isEqualTo(runs);
        assertThat(count("user_action_events")).isEqualTo(actions);
        assertThat(count("guidance_eval_runs")).isEqualTo(evalRuns);

        mockMvc.perform(asDev(post("/api/v1/admin/guidance/runs/" + runId + "/evaluate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.evalRunId").exists())
                .andExpect(jsonPath("$.replayMode").value("FROZEN_REPLAY"));

        assertThat(count("fishing_sessions")).isEqualTo(sessions);
        assertThat(count("agent_runs")).isEqualTo(runs);
        assertThat(count("user_action_events")).isEqualTo(actions);
        assertThat(count("outcome_attributions")).isEqualTo(outcomes);
        assertThat(count("agent_delivered_decisions")).isEqualTo(delivered);
        assertThat(count("guidance_eval_runs")).isEqualTo(evalRuns + 1);

        entityManager.clear();
        AgentRunEntity unchanged = agentRunRepository.findById(runId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(unchanged.getVisibility()).isEqualTo(AgentRunVisibility.PRODUCTION);
        assertThat(unchanged.getFallbackReason()).isNull();
    }

    private UUID insertSession() {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into fishing_sessions (
                            id, trip_id, user_id, started_at, status, total_paused_seconds,
                            activity_state, activity_state_source, created_at
                        ) values (?, ?, ?, now(), 'COMPLETED', 0, 'UNKNOWN', 'UNKNOWN', now())
                        """,
                sessionId,
                trip.getId(),
                DevSeedIds.USER_ID
        );
        return sessionId;
    }

    private UUID insertRun(UUID sessionId) {
        Instant now = Instant.parse("2026-09-18T14:00:00Z");
        UUID runId = UUID.randomUUID();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        run.setFishingSessionId(sessionId);
        run.setTrigger(GuidanceTrigger.USER_REQUEST);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setVisibility(AgentRunVisibility.PRODUCTION);
        run.setStateSnapshot(GuidanceContracts.mapper().convertValue(EvalFixtures.deterministicStay().state(), MAP));
        run.setContextSnapshot(GuidanceContracts.mapper().convertValue(EvalFixtures.deterministicStay().context(), MAP));
        run.setPromptVersion("guidance-prompt-v1");
        run.setAgentPolicyVersion("v1");
        run.setStartedAt(now);
        run.setFinishedAt(now.plusSeconds(2));
        agentRunRepository.saveAndFlush(run);
        return runId;
    }

    private int count(String table) {
        Integer rows = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return rows == null ? 0 : rows;
    }
}
