package com.aifishing.guidance.persist;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.attribution.DeliveredSnapshot;
import com.aifishing.guidance.attribution.OutcomeAttributor;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.eval.EvalFixtures;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowIsolationIT extends AbstractIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final Instant T0 = Instant.parse("2026-09-18T14:00:00Z");

    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private AgentDeliveredDecisionRepository deliveredRepository;
    @Autowired
    private DecisionPersistence decisionPersistence;
    @Autowired
    private ShadowAgentRunWriter shadowAgentRunWriter;
    @Autowired
    private OutcomeAttributor outcomeAttributor;

    @Test
    void writerNeverWritesDeliveredHorizonOrLearning() {
        UUID sessionId = insertSession();
        int delivered = count("agent_delivered_decisions");
        int horizons = count("guidance_plan_versions");
        int learning = count("guidance_learning_outbox");
        var fixture = EvalFixtures.deterministicStay();
        var snapshot = new com.aifishing.guidance.contracts.FrozenAgentRunSnapshot(
                fixture.schemaVersion(),
                fixture.runId(),
                sessionId,
                fixture.trigger(),
                fixture.state(),
                fixture.context(),
                fixture.retrievedMemory(),
                fixture.recordedToolObservations(),
                fixture.recordedClock(),
                fixture.componentVersions()
        );
        AgentRunResult shadow = new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                UUID.randomUUID(),
                AgentRunStatus.COMPLETED,
                null,
                List.of(),
                GuidancePhase2Fixtures.candidate(GuidanceAction.MOVE, null, GuidancePhase2Fixtures.SPOT_2, List.of(), 20),
                new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of()),
                stay("shadow")
        );

        UUID shadowId = shadowAgentRunWriter.persist(snapshot, shadow, "v2");
        entityManager.clear();

        AgentRunEntity stored = agentRunRepository.findById(shadowId).orElseThrow();
        assertThat(stored.getVisibility()).isEqualTo(AgentRunVisibility.SHADOW);
        assertThat(stored.getTriggerOutboxId()).isNull();
        assertThat(deliveredRepository.findFirstByRunId(shadowId)).isEmpty();
        assertThat(count("agent_delivered_decisions")).isEqualTo(delivered);
        assertThat(count("guidance_plan_versions")).isEqualTo(horizons);
        assertThat(count("guidance_learning_outbox")).isEqualTo(learning);
        assertThat(decisionPersistence.current(sessionId)).isEmpty();
    }

    @Test
    void productionReadersIgnoreNewerShadowRows() {
        UUID sessionId = insertSession();
        UUID productionId = insertRun(sessionId, AgentRunVisibility.PRODUCTION, T0);
        UUID productionDeliveredId = insertDelivered(productionId, stay("production"));
        UUID shadowId = insertRun(sessionId, AgentRunVisibility.SHADOW, T0.plusSeconds(60));
        UUID shadowDeliveredId = insertDelivered(shadowId, stay("shadow"));
        entityManager.clear();

        assertThat(agentRunRepository.findFirstByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                sessionId, AgentRunVisibility.PRODUCTION
        ).orElseThrow().getId()).isEqualTo(productionId);
        assertThat(agentRunRepository.findFirstByFishingSessionIdOrderByStartedAtDesc(sessionId)
                .orElseThrow().getId()).isEqualTo(shadowId);

        DeliveredDecision current = decisionPersistence.current(sessionId).orElseThrow();
        assertThat(current.shortExplanation()).isEqualTo("production");

        assertThat(deliveredRepository.findByFishingSessionIdOrderByCreatedAtAsc(sessionId))
                .extracting(AgentDeliveredDecisionEntity::getId)
                .containsExactly(productionDeliveredId)
                .doesNotContain(shadowDeliveredId);

        assertThat(outcomeAttributor.loadDecisions(sessionId))
                .extracting(DeliveredSnapshot::runId)
                .containsExactly(productionId)
                .doesNotContain(shadowId);
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

    private UUID insertRun(UUID sessionId, AgentRunVisibility visibility, Instant startedAt) {
        UUID runId = UUID.randomUUID();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        run.setFishingSessionId(sessionId);
        run.setTrigger(GuidanceTrigger.USER_REQUEST);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setVisibility(visibility);
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusSeconds(2));
        agentRunRepository.saveAndFlush(run);
        return runId;
    }

    private UUID insertDelivered(UUID runId, DeliveredDecision decision) {
        AgentDeliveredDecisionEntity row = new AgentDeliveredDecisionEntity();
        row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        row.setRunId(runId);
        row.setDecision(GuidanceContracts.mapper().convertValue(decision, MAP));
        row.setFallbackUsed(false);
        deliveredRepository.saveAndFlush(row);
        return row.getId();
    }

    private static DeliveredDecision stay(String explanation) {
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
                20,
                List.of("TEST"),
                explanation,
                List.of(),
                false,
                null,
                0.8
        );
    }

    private int count(String table) {
        Integer rows = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return rows == null ? 0 : rows;
    }
}
