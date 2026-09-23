package com.aifishing.guidance.horizon;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.AgentRunRequest;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.PlanCreatedBy;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.runtime.KillSwitchContinuation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceHorizonWriterTest {

    private static final UUID VERSION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private GuidancePlanVersionRepository versionRepository;
    @Mock
    private GuidancePlanStepRepository stepRepository;

    private GuidanceHorizonWriter writer;

    @BeforeEach
    void setUp() {
        writer = new GuidanceHorizonWriter(versionRepository, stepRepository);
    }

    @Test
    void emptyOrMissingHorizonDoesNotPersistAVersion() {
        writer.writeAfterDelivered(SESSION_ID, (AgentRunResult) null);
        writer.writeAfterDelivered(SESSION_ID, failedWithoutDelivered());
        writer.writeAfterDelivered(SESSION_ID, delivered(List.of()));

        verify(versionRepository, never()).save(any());
        verify(stepRepository, never()).saveAll(any());
    }

    @Test
    void stayAtXThenMoveSpot3PersistsExactlyThoseStepsWithoutReinsertingSpot2() {
        stubFirstVersion();
        List<HorizonStep> fixtureA = List.of(
                new HorizonStep(1, GuidanceAction.STAY, null, 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, SPOT_3, null, false)
        );

        writer.writeAfterDelivered(SESSION_ID, delivered(fixtureA));

        ArgumentCaptor<List<GuidancePlanStepEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(stepRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getType)
                .containsExactly(GuidanceAction.STAY, GuidanceAction.MOVE);
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getTripWaypointId)
                .containsExactly(null, SPOT_3);
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getTripWaypointId)
                .doesNotContain(SPOT_2);
        ArgumentCaptor<GuidancePlanVersionEntity> version = ArgumentCaptor.forClass(GuidancePlanVersionEntity.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getFishingSessionId()).isEqualTo(SESSION_ID);
        assertThat(version.getValue().getCreatedBy()).isEqualTo(PlanCreatedBy.AGENT);
        assertThat(version.getValue().getReplanReason()).isNull();
    }

    @Test
    void stayAtXThenMoveSpot2IsAlsoValidAndPersistedAsProposed() {
        stubFirstVersion();
        List<HorizonStep> fixtureB = List.of(
                new HorizonStep(1, GuidanceAction.STAY, null, 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, SPOT_2, null, false)
        );

        writer.writeAfterDelivered(SESSION_ID, delivered(fixtureB));

        ArgumentCaptor<List<GuidancePlanStepEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(stepRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getTripWaypointId)
                .containsExactly(null, SPOT_2);
    }

    @Test
    void aSecondStayOnTheSameStopDoesNotAddAnotherDwell() {
        stubFirstVersion();
        List<HorizonStep> proposed = List.of(
                new HorizonStep(1, GuidanceAction.STAY, SPOT_2, 20, true),
                new HorizonStep(2, GuidanceAction.STAY, SPOT_2, 20, false),
                new HorizonStep(3, GuidanceAction.MOVE, SPOT_3, null, false)
        );

        writer.writeAfterDelivered(SESSION_ID, delivered(proposed));

        ArgumentCaptor<List<GuidancePlanStepEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(stepRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getType)
                .containsExactly(GuidanceAction.STAY, GuidanceAction.MOVE);
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getTripWaypointId)
                .containsExactly(SPOT_2, SPOT_3);
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getDurationMinutes)
                .containsExactly(20, null);
    }

    @Test
    void failedRunWithEmptyHorizonDoesNotWriteAPartialVersion() {
        writer.writeAfterDelivered(
                SESSION_ID,
                new AgentRunResult(
                        GuidanceSchemaVersion.VALUE,
                        UUID.randomUUID(),
                        AgentRunStatus.FAILED,
                        null,
                        List.of(),
                        null,
                        null,
                        delivered(List.of())
                )
        );
        verify(versionRepository, never()).save(any());
        verify(stepRepository, never()).saveAll(any());
    }

    @Test
    void completeFallbackStayHorizonIsWrittenWithoutInventingMissingSpots() {
        stubFirstVersion();
        DeliveredDecision fallback = GuidanceFallback.stay(
                GuidancePhase2Fixtures.DECISION_ID,
                GuidancePhase2Fixtures.safeState(),
                GuidanceFallback.RUN_TIMEOUT,
                "Timed out"
        );

        writer.writeAfterDelivered(SESSION_ID, fallback);

        ArgumentCaptor<List<GuidancePlanStepEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(stepRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().getFirst().getType()).isEqualTo(GuidanceAction.STAY);
        assertThat(saved.getValue()).extracting(GuidancePlanStepEntity::getTripWaypointId)
                .doesNotContain(SPOT_2, SPOT_3);
    }

    @Test
    void killSwitchContinuationDoesNotWriteHorizon() {
        writer.writeAfterDelivered(
                SESSION_ID,
                KillSwitchContinuation.from(GuidancePhase2Fixtures.DECISION_ID, GuidancePhase2Fixtures.safeState())
        );

        verify(versionRepository, never()).save(any());
        verify(stepRepository, never()).saveAll(any());
    }

    private void stubFirstVersion() {
        when(versionRepository.findFirstByFishingSessionIdOrderByVersionDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(invocation -> {
            GuidancePlanVersionEntity entity = invocation.getArgument(0);
            entity.setId(VERSION_ID);
            return entity;
        });
    }

    private static AgentRunResult failedWithoutDelivered() {
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                UUID.randomUUID(),
                AgentRunStatus.FAILED,
                (AgentRunRequest) null,
                List.of(),
                null,
                null,
                null
        );
    }

    private static DeliveredDecision delivered(List<HorizonStep> horizon) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                GuidanceAction.STAY,
                GuidanceAction.MOVE,
                SPOT_3,
                null,
                null,
                null,
                null,
                null,
                20,
                List.of("USER_STARTED_AD_HOC_FISHING"),
                "Stay at X then continue",
                horizon,
                false,
                null,
                0.8
        );
    }
}
