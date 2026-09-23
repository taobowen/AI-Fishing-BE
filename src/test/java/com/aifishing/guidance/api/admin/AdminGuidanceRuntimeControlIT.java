package com.aifishing.guidance.api.admin;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGuidanceRuntimeControlIT extends AbstractIntegrationTest {

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Test
    void getAndPatchRollbackWithoutMutatingHistoricalRuns() throws Exception {
        mockMvc.perform(asDev(get("/api/v1/admin/guidance/runtime-control")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentEnabled").value(true))
                .andExpect(jsonPath("$.productionVersion").value(AgentPolicyVersion.V1))
                .andExpect(jsonPath("$.shadowEnabled").value(false))
                .andExpect(jsonPath("$.learningEnabled").value(true));

        UUID runId = insertHistoricalRun(AgentPolicyVersion.V2);

        mockMvc.perform(asDev(patch("/api/v1/admin/guidance/runtime-control"))
                        .content("""
                                {"productionVersion":"v1","candidateVersion":"v2","shadowEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionVersion").value(AgentPolicyVersion.V1))
                .andExpect(jsonPath("$.candidateVersion").value(AgentPolicyVersion.V2))
                .andExpect(jsonPath("$.shadowEnabled").value(true));

        entityManager.clear();
        assertThat(agentRunRepository.findById(runId).orElseThrow().getAgentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V2);

        mockMvc.perform(asDev(patch("/api/v1/admin/guidance/runtime-control"))
                        .content("""
                                {"productionVersion":"v99"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unknown agent policy version")));

        mockMvc.perform(asDev(patch("/api/v1/admin/guidance/runtime-control"))
                        .content("""
                                {"candidateVersion":"ghost"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unknown agent policy version")));

        entityManager.clear();
        assertThat(agentRunRepository.findById(runId).orElseThrow().getAgentPolicyVersion())
                .isEqualTo(AgentPolicyVersion.V2);
        assertThat(jdbcTemplate.queryForObject(
                "select production_version from agent_runtime_control where id = 1",
                String.class
        )).isEqualTo(AgentPolicyVersion.V1);
    }

    private UUID insertHistoricalRun(String policyVersion) {
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
        UUID runId = UUID.randomUUID();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        run.setFishingSessionId(sessionId);
        run.setTrigger(GuidanceTrigger.USER_REQUEST);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setAgentPolicyVersion(policyVersion);
        run.setLearningAlgorithmVersion("1");
        run.setStartedAt(Instant.parse("2026-09-18T14:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-09-18T14:00:02Z"));
        agentRunRepository.saveAndFlush(run);
        return runId;
    }
}
