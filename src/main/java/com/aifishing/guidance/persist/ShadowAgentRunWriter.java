package com.aifishing.guidance.persist;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationEntity;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists live candidate shadow on {@code agent_runs.visibility=SHADOW}.
 * Snapshots, tools, candidate, and validation are written. SHADOW never writes
 * {@code agent_delivered_decisions}, horizon, {@code guidance/current},
 * notifications, or learning. Task D: assert cooldown/current/latest-production
 * ignore these rows.
 */
@Component
public class ShadowAgentRunWriter {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AgentRunRepository agentRunRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final AgentCandidateDecisionRepository candidateRepository;
    private final AgentValidationRepository validationRepository;
    private final Clock clock;

    public ShadowAgentRunWriter(
            AgentRunRepository agentRunRepository,
            AgentToolCallRepository toolCallRepository,
            AgentCandidateDecisionRepository candidateRepository,
            AgentValidationRepository validationRepository,
            Clock clock
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
        this.candidateRepository = candidateRepository;
        this.validationRepository = validationRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID persist(FrozenAgentRunSnapshot snapshot, AgentRunResult shadow, String candidateVersion) {
        if (snapshot == null || shadow == null) {
            return null;
        }
        UUID runId = shadow.runId() == null ? UUID.randomUUID() : shadow.runId();
        Instant now = clock.instant();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(runId);
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(snapshot.sessionId());
        entity.setTrigger(snapshot.trigger());
        entity.setStatus(shadow.status() == null || shadow.status() == AgentRunStatus.RUNNING
                ? AgentRunStatus.COMPLETED
                : shadow.status());
        entity.setVisibility(AgentRunVisibility.SHADOW);
        entity.setTriggerOutboxId(null);
        entity.setRelatedTriggers(List.of());
        entity.setTriggerReasonCodes(List.of());
        entity.setStateSnapshot(asMap(stateOf(snapshot, shadow)));
        entity.setContextSnapshot(asMap(contextOf(snapshot, shadow)));
        entity.setAgentPolicyVersion(candidateVersion);
        entity.setStartedAt(snapshot.recordedClock() == null ? now : snapshot.recordedClock());
        entity.setFinishedAt(now);
        if (shadow.delivered() != null) {
            entity.setFallbackReason(shadow.delivered().fallbackReason());
        }
        agentRunRepository.saveAndFlush(entity);

        if (shadow.toolCalls() != null) {
            for (ToolCallRecord toolCall : shadow.toolCalls()) {
                if (toolCall == null || toolCall.request() == null || toolCall.request().toolName() == null) {
                    continue;
                }
                AgentToolCallEntity row = new AgentToolCallEntity();
                row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
                row.setRunId(runId);
                row.setToolName(toolCall.request().toolName());
                row.setRequest(asMap(toolCall.request()));
                row.setResult(asMap(toolCall.result()));
                row.setLatencyMs(toolCall.latencyMs());
                row.setObservedAt(toolCall.observedAt() == null ? now : toolCall.observedAt());
                toolCallRepository.save(row);
            }
        }
        persistCandidate(runId, shadow.candidate());
        persistValidation(runId, shadow.validation());
        return runId;
    }

    private void persistCandidate(UUID runId, CandidateDecision candidate) {
        if (candidate == null) {
            return;
        }
        AgentCandidateDecisionEntity row = new AgentCandidateDecisionEntity();
        row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        row.setRunId(runId);
        row.setDecision(asMap(candidate));
        candidateRepository.save(row);
    }

    private void persistValidation(UUID runId, DecisionValidationResult validation) {
        if (validation == null) {
            return;
        }
        AgentValidationEntity row = new AgentValidationEntity();
        row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        row.setRunId(runId);
        row.setResult(asMap(validation));
        validationRepository.save(row);
    }

    private static FishingSessionState stateOf(FrozenAgentRunSnapshot snapshot, AgentRunResult shadow) {
        if (shadow.request() != null && shadow.request().state() != null) {
            return shadow.request().state();
        }
        return snapshot.state();
    }

    private static FishingAgentContext contextOf(FrozenAgentRunSnapshot snapshot, AgentRunResult shadow) {
        if (shadow.request() != null && shadow.request().context() != null) {
            return shadow.request().context();
        }
        return snapshot.context();
    }

    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return GuidanceContracts.mapper().convertValue(value, MAP);
    }
}
