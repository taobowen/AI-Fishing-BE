package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.spi.AgentRunSnapshotLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads a frozen snapshot from persisted {@code agent_runs} plus recorded tool rows.
 * Never resolves live weather or memory.
 */
@Component
public class JpaAgentRunSnapshotLoader implements AgentRunSnapshotLoader {

    private final AgentRunRepository agentRunRepository;
    private final AgentToolCallRepository toolCallRepository;

    public JpaAgentRunSnapshotLoader(
            AgentRunRepository agentRunRepository,
            AgentToolCallRepository toolCallRepository
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FrozenAgentRunSnapshot> load(UUID runId) {
        if (runId == null) {
            return Optional.empty();
        }
        return agentRunRepository.findById(runId).flatMap(this::toSnapshot);
    }

    private Optional<FrozenAgentRunSnapshot> toSnapshot(AgentRunEntity run) {
        FishingSessionState state = convert(run.getStateSnapshot(), FishingSessionState.class);
        if (state == null) {
            return Optional.empty();
        }
        FishingAgentContext context = convert(run.getContextSnapshot(), FishingAgentContext.class);
        RetrievedMemory memory = memoryFrom(context, run.getMemoryRefIds());
        List<ToolCallRecord> tools = new ArrayList<>();
        for (AgentToolCallEntity row : toolCallRepository.findByRunIdOrderByObservedAtAsc(run.getId())) {
            tools.add(toToolCall(row));
        }
        return Optional.of(new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                run.getId(),
                run.getFishingSessionId(),
                run.getTrigger(),
                state,
                context,
                memory,
                tools,
                run.getStartedAt(),
                versionsOf(run)
        ));
    }

    private static RetrievedMemory memoryFrom(FishingAgentContext context, List<String> memoryRefIds) {
        if (context != null) {
            return new RetrievedMemory(
                    context.userPreferences(),
                    context.inferredPreferences(),
                    context.retrievedMemoryIds() == null || context.retrievedMemoryIds().isEmpty()
                            ? memoryRefIds
                            : context.retrievedMemoryIds()
            );
        }
        return new RetrievedMemory(null, List.of(), memoryRefIds == null ? List.of() : memoryRefIds);
    }

    private static ToolCallRecord toToolCall(AgentToolCallEntity row) {
        ToolRequestEnvelope request = convert(row.getRequest(), ToolRequestEnvelope.class);
        ToolResultEnvelope result = convert(row.getResult(), ToolResultEnvelope.class);
        return new ToolCallRecord(
                GuidanceSchemaVersion.VALUE,
                request,
                result,
                row.getLatencyMs(),
                row.getObservedAt()
        );
    }

    private static EvalComponentVersions versionsOf(AgentRunEntity run) {
        return new EvalComponentVersions(
                run.getPromptVersion(),
                run.getModelProvider(),
                run.getModelName(),
                run.getModelVersion(),
                run.getToolSchemaVersion(),
                run.getContextVersion(),
                null,
                null,
                null,
                run.getAgentPolicyVersion(),
                run.getLearningAlgorithmVersion(),
                run.getLearningSnapshotVersion()
        );
    }

    private static <T> T convert(Map<String, Object> value, Class<T> type) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return GuidanceContracts.mapper().convertValue(value, type);
    }
}
