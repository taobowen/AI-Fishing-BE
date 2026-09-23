package com.aifishing.guidance.persist;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.eval.EvalFixtures;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationEntity;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShadowAgentRunWriterTest {

    private static final Instant NOW = Instant.parse("2026-09-18T14:00:00Z");

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private AgentToolCallRepository toolCallRepository;
    @Mock
    private AgentCandidateDecisionRepository candidateRepository;
    @Mock
    private AgentValidationRepository validationRepository;

    private ShadowAgentRunWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ShadowAgentRunWriter(
                agentRunRepository,
                toolCallRepository,
                candidateRepository,
                validationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void persistWritesShadowSnapshotsWithoutOutboxOrDelivered() {
        when(agentRunRepository.saveAndFlush(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRunResult shadow = new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                UUID.fromString("dddddddd-0000-4000-8000-000000000001"),
                AgentRunStatus.COMPLETED,
                null,
                List.of(),
                GuidancePhase2Fixtures.candidate(GuidanceAction.STAY, null, GuidancePhase2Fixtures.TRIP_WAYPOINT, List.of(), 20),
                new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of()),
                stayDelivered()
        );

        UUID runId = writer.persist(EvalFixtures.deterministicStay(), shadow, "v2");

        ArgumentCaptor<AgentRunEntity> run = ArgumentCaptor.forClass(AgentRunEntity.class);
        verify(agentRunRepository).saveAndFlush(run.capture());
        assertThat(runId).isEqualTo(shadow.runId());
        assertThat(run.getValue().getVisibility()).isEqualTo(AgentRunVisibility.SHADOW);
        assertThat(run.getValue().getTriggerOutboxId()).isNull();
        assertThat(run.getValue().getRelatedTriggers()).isEmpty();
        assertThat(run.getValue().getTriggerReasonCodes()).isEmpty();
        assertThat(run.getValue().getAgentPolicyVersion()).isEqualTo("v2");
        verify(candidateRepository).save(any(AgentCandidateDecisionEntity.class));
        verify(validationRepository).save(any(AgentValidationEntity.class));
        verify(toolCallRepository, never()).save(any());
    }

    @Test
    void nullInputsWriteNothing() {
        assertThat(writer.persist(null, stayResult(), "v2")).isNull();
        assertThat(writer.persist(EvalFixtures.deterministicStay(), null, "v2")).isNull();
        verify(agentRunRepository, never()).saveAndFlush(any());
    }

    private static AgentRunResult stayResult() {
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                UUID.randomUUID(),
                AgentRunStatus.COMPLETED,
                null,
                List.of(),
                null,
                null,
                stayDelivered()
        );
    }

    private static com.aifishing.guidance.contracts.DeliveredDecision stayDelivered() {
        return new com.aifishing.guidance.contracts.DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.STAY,
                null,
                GuidancePhase2Fixtures.TRIP_WAYPOINT,
                com.aifishing.common.enums.LureFamily.TUBE,
                com.aifishing.common.enums.PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                com.aifishing.guidance.contracts.RetrieveStyle.SLOW,
                20,
                List.of("SHADOW"),
                "Shadow stay",
                List.of(),
                false,
                null,
                0.8
        );
    }
}
