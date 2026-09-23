package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaAgentRunSnapshotLoaderTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Test
    void loadReplaysPersistedStateContextToolsAndClockWithoutLiveData() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentToolCallRepository tools = mock(AgentToolCallRepository.class);
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(EvalFixtures.RUN_STAY);
        entity.setFishingSessionId(EvalFixtures.SESSION_ID);
        entity.setTrigger(GuidanceTrigger.USER_REQUEST);
        entity.setStateSnapshot(GuidanceContracts.mapper().convertValue(EvalFixtures.state(
                com.aifishing.guidance.contracts.WeatherCondition.CLOUDY, 16.0), MAP));
        entity.setContextSnapshot(GuidanceContracts.mapper().convertValue(
                EvalFixtures.context(GuidanceTrigger.USER_REQUEST), MAP));
        entity.setMemoryRefIds(List.of("mem-frozen"));
        entity.setStartedAt(EvalFixtures.CLOCK);
        entity.setPromptVersion("guidance-prompt-v1");
        entity.setModelProvider("deterministic");
        entity.setModelName("deterministic");
        entity.setModelVersion("v1");
        when(runs.findById(EvalFixtures.RUN_STAY)).thenReturn(Optional.of(entity));

        AgentToolCallEntity tool = new AgentToolCallEntity();
        tool.setRunId(EvalFixtures.RUN_STAY);
        tool.setToolName(ToolName.GET_NEARBY_WAYPOINTS);
        tool.setRequest(GuidanceContracts.mapper().convertValue(EvalFixtures.recordedNearby().request(), MAP));
        tool.setResult(GuidanceContracts.mapper().convertValue(EvalFixtures.recordedNearby().result(), MAP));
        tool.setLatencyMs(12);
        tool.setObservedAt(EvalFixtures.CLOCK);
        when(tools.findByRunIdOrderByObservedAtAsc(EvalFixtures.RUN_STAY)).thenReturn(List.of(tool));

        Optional<FrozenAgentRunSnapshot> loaded = new JpaAgentRunSnapshotLoader(runs, tools).load(EvalFixtures.RUN_STAY);
        assertThat(loaded).isPresent();
        FrozenAgentRunSnapshot snapshot = loaded.orElseThrow();
        assertThat(snapshot.runId()).isEqualTo(EvalFixtures.RUN_STAY);
        assertThat(snapshot.sessionId()).isEqualTo(EvalFixtures.SESSION_ID);
        assertThat(snapshot.trigger()).isEqualTo(GuidanceTrigger.USER_REQUEST);
        assertThat(snapshot.state().environment().weather().name()).isEqualTo("CLOUDY");
        assertThat(snapshot.context().trigger()).isEqualTo(GuidanceTrigger.USER_REQUEST);
        assertThat(snapshot.retrievedMemory().retrievedMemoryIds()).contains("mem-frozen");
        assertThat(snapshot.recordedToolObservations()).hasSize(1);
        assertThat(snapshot.recordedClock()).isEqualTo(EvalFixtures.CLOCK);
        assertThat(snapshot.componentVersions().modelProvider()).isEqualTo("deterministic");
        assertThat(snapshot.componentVersions().agentPolicyVersion()).isNull();
        assertThat(snapshot.componentVersions().learningAlgorithmVersion()).isNull();
        assertThat(snapshot.componentVersions().learningSnapshotVersion()).isNull();
    }

    @Test
    void historicalNullAgentPolicyVersionStillLoads() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentToolCallRepository tools = mock(AgentToolCallRepository.class);
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(EvalFixtures.RUN_STAY);
        entity.setFishingSessionId(EvalFixtures.SESSION_ID);
        entity.setTrigger(GuidanceTrigger.USER_REQUEST);
        entity.setStateSnapshot(GuidanceContracts.mapper().convertValue(EvalFixtures.state(
                com.aifishing.guidance.contracts.WeatherCondition.CLOUDY, 16.0), MAP));
        entity.setStartedAt(EvalFixtures.CLOCK);
        entity.setPromptVersion("guidance-prompt-v1");
        entity.setAgentPolicyVersion(null);
        entity.setLearningAlgorithmVersion(null);
        entity.setLearningSnapshotVersion(null);
        when(runs.findById(EvalFixtures.RUN_STAY)).thenReturn(Optional.of(entity));
        when(tools.findByRunIdOrderByObservedAtAsc(EvalFixtures.RUN_STAY)).thenReturn(List.of());

        FrozenAgentRunSnapshot snapshot = new JpaAgentRunSnapshotLoader(runs, tools)
                .load(EvalFixtures.RUN_STAY)
                .orElseThrow();
        assertThat(snapshot.state()).isNotNull();
        assertThat(snapshot.componentVersions().promptVersion()).isEqualTo("guidance-prompt-v1");
        assertThat(snapshot.componentVersions().agentPolicyVersion()).isNull();
        assertThat(snapshot.componentVersions().learningSnapshotVersion()).isNull();
    }

    @Test
    void missingStateSnapshotIsEmpty() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentToolCallRepository tools = mock(AgentToolCallRepository.class);
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(UUID.fromString("aaaaaaaa-0001-4000-8000-000000000099"));
        entity.setFishingSessionId(EvalFixtures.SESSION_ID);
        entity.setTrigger(GuidanceTrigger.USER_REQUEST);
        when(runs.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(tools.findByRunIdOrderByObservedAtAsc(entity.getId())).thenReturn(List.of());

        assertThat(new JpaAgentRunSnapshotLoader(runs, tools).load(entity.getId())).isEmpty();
    }
}
