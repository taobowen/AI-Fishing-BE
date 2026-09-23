package com.aifishing.guidance.persist;

import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaDecisionPersistenceCurrentTest {

    private static final UUID SESSION = UUID.fromString("eeeeeeee-0000-4000-8000-000000000001");
    private static final UUID PRODUCTION = UUID.fromString("eeeeeeee-0000-4000-8000-000000000010");
    private static final Instant T0 = Instant.parse("2026-09-18T14:00:00Z");

    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private AgentToolCallRepository toolCallRepository;
    @Mock
    private AgentCandidateDecisionRepository candidateRepository;
    @Mock
    private AgentValidationRepository validationRepository;
    @Mock
    private AgentDeliveredDecisionRepository deliveredRepository;
    @Mock
    private GuidanceTriggerOutboxRepository triggerOutboxRepository;

    private JpaDecisionPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new JpaDecisionPersistence(
                agentRunRepository,
                toolCallRepository,
                candidateRepository,
                validationRepository,
                deliveredRepository,
                triggerOutboxRepository,
                Clock.fixed(T0, ZoneOffset.UTC)
        );
    }

    @Test
    void currentReadsProductionRunsOnly() {
        AgentRunEntity production = new AgentRunEntity();
        production.setId(PRODUCTION);
        production.setVisibility(AgentRunVisibility.PRODUCTION);
        when(agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                SESSION, AgentRunVisibility.PRODUCTION
        )).thenReturn(List.of(production));
        AgentDeliveredDecisionEntity delivered = new AgentDeliveredDecisionEntity();
        delivered.setRunId(PRODUCTION);
        delivered.setDecision(Map.of(
                "schemaVersion", GuidanceSchemaVersion.VALUE,
                "primaryAction", "STAY",
                "reevaluateAfterMinutes", 20,
                "fallbackUsed", false
        ));
        when(deliveredRepository.findFirstByRunId(PRODUCTION)).thenReturn(Optional.of(delivered));

        Optional<DeliveredDecision> current = persistence.current(SESSION);

        assertThat(current).isPresent();
        assertThat(current.orElseThrow().primaryAction()).isEqualTo(GuidanceAction.STAY);
        verify(agentRunRepository, never()).findByFishingSessionIdOrderByStartedAtDesc(any());
        verify(agentRunRepository, never()).findFirstByFishingSessionIdOrderByStartedAtDesc(any());
        verify(agentRunRepository).findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                SESSION, AgentRunVisibility.PRODUCTION
        );
    }

    @Test
    void currentIsEmptyWhenOnlyShadowWouldExist() {
        when(agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                SESSION, AgentRunVisibility.PRODUCTION
        )).thenReturn(List.of());

        assertThat(persistence.current(SESSION)).isEmpty();
        verify(deliveredRepository, never()).findFirstByRunId(any());
    }
}
