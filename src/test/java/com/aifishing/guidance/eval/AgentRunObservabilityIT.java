package com.aifishing.guidance.eval;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
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

class AgentRunObservabilityIT extends AbstractIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private JpaAgentRunSnapshotLoader snapshotLoader;

    @Test
    void loadByRunIdReturnsVersionSnapshotDecisionValidationAndUsage() {
        UUID sessionId = insertSession();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-18T14:00:00Z");
        FrozenAgentRunSnapshot fixture = EvalFixtures.deterministicStay();

        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        run.setFishingSessionId(sessionId);
        run.setTrigger(GuidanceTrigger.USER_REQUEST);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setStateSnapshot(GuidanceContracts.mapper().convertValue(fixture.state(), MAP));
        run.setContextSnapshot(GuidanceContracts.mapper().convertValue(fixture.context(), MAP));
        run.setModelProvider("deterministic");
        run.setModelName("deterministic");
        run.setModelVersion("v1");
        run.setPromptVersion("guidance-prompt-v1");
        run.setToolSchemaVersion("guidance-tools-v1");
        run.setContextVersion("guidance-context-v1");
        run.setAgentPolicyVersion("v1");
        run.setLearningAlgorithmVersion("1");
        run.setLearningSnapshotVersion(null);
        run.setDecisionId(runId);
        run.setStartedAt(now);
        run.setFinishedAt(now.plusSeconds(2));
        run.setUsageTelemetry(Map.of(
                "modelProvider", "deterministic",
                "promptVersion", "guidance-prompt-v1"
        ));
        run.setRawProviderUsage(Map.of("id", "resp-1"));
        agentRunRepository.saveAndFlush(run);

        jdbcTemplate.update(
                """
                        insert into agent_candidate_decisions (id, run_id, schema_version, decision)
                        values (?, ?, 'guidance.contracts.v1', '{"primaryAction":"STAY"}'::jsonb)
                        """,
                UUID.randomUUID(),
                runId
        );
        jdbcTemplate.update(
                """
                        insert into agent_validations (id, run_id, schema_version, result)
                        values (?, ?, 'guidance.contracts.v1', '{"valid":true}'::jsonb)
                        """,
                UUID.randomUUID(),
                runId
        );
        jdbcTemplate.update(
                """
                        insert into agent_delivered_decisions (
                            id, run_id, schema_version, decision, fallback_used
                        ) values (?, ?, 'guidance.contracts.v1', '{"primaryAction":"STAY"}'::jsonb, false)
                        """,
                UUID.randomUUID(),
                runId
        );
        jdbcTemplate.update(
                """
                        insert into agent_tool_calls (
                            id, run_id, schema_version, tool_name, request, result, latency_ms, observed_at
                        ) values (?, ?, 'guidance.contracts.v1', 'get_nearby_waypoints',
                            '{"toolName":"GET_NEARBY_WAYPOINTS"}'::jsonb,
                            '{"status":"OK"}'::jsonb, 12, ?)
                        """,
                UUID.randomUUID(),
                runId,
                java.sql.Timestamp.from(now)
        );
        entityManager.clear();

        AgentRunEntity loaded = agentRunRepository.findById(runId).orElseThrow();
        assertThat(loaded.getAgentPolicyVersion()).isEqualTo("v1");
        assertThat(loaded.getLearningAlgorithmVersion()).isEqualTo("1");
        assertThat(loaded.getLearningSnapshotVersion()).isNull();
        assertThat(loaded.getStateSnapshot()).isNotEmpty();
        assertThat(loaded.getContextSnapshot()).isNotEmpty();
        assertThat(loaded.getDecisionId()).isEqualTo(runId);
        assertThat(loaded.getStartedAt()).isEqualTo(now);
        assertThat(loaded.getFinishedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(loaded.getUsageTelemetry()).containsEntry("modelProvider", "deterministic");
        assertThat(loaded.getRawProviderUsage()).containsEntry("id", "resp-1");

        FrozenAgentRunSnapshot snapshot = snapshotLoader.load(runId).orElseThrow();
        assertThat(snapshot.componentVersions().agentPolicyVersion()).isEqualTo("v1");
        assertThat(snapshot.componentVersions().learningSnapshotVersion()).isNull();
        assertThat(snapshot.state()).isNotNull();
        assertThat(snapshot.context()).isNotNull();
        assertThat(snapshot.recordedToolObservations()).hasSize(1);

        assertThat(count("agent_candidate_decisions", runId)).isEqualTo(1);
        assertThat(count("agent_validations", runId)).isEqualTo(1);
        assertThat(count("agent_delivered_decisions", runId)).isEqualTo(1);
        assertThat(count("agent_tool_calls", runId)).isEqualTo(1);
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

    private int count(String table, UUID runId) {
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where run_id = ?",
                Integer.class,
                runId
        );
        return rows == null ? 0 : rows;
    }
}
