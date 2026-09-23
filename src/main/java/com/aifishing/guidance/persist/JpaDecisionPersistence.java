package com.aifishing.guidance.persist;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.AgentToolCallEntity;
import com.aifishing.guidance.persistence.AgentToolCallRepository;
import com.aifishing.guidance.persistence.AgentValidationEntity;
import com.aifishing.guidance.persistence.AgentValidationRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.runtime.AgentRunMetadata;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Audit lifecycle with independent commits so RUNNING is visible before the
 * model returns. Candidate and delivered stay on separate tables.
 */
@Component
public class JpaDecisionPersistence implements DecisionPersistence {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AgentRunRepository agentRunRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final AgentCandidateDecisionRepository candidateRepository;
    private final AgentValidationRepository validationRepository;
    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final GuidanceTriggerOutboxRepository triggerOutboxRepository;
    private final Clock clock;

    public JpaDecisionPersistence(
            AgentRunRepository agentRunRepository,
            AgentToolCallRepository toolCallRepository,
            AgentCandidateDecisionRepository candidateRepository,
            AgentValidationRepository validationRepository,
            AgentDeliveredDecisionRepository deliveredRepository,
            GuidanceTriggerOutboxRepository triggerOutboxRepository,
            Clock clock
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
        this.candidateRepository = candidateRepository;
        this.validationRepository = validationRepository;
        this.deliveredRepository = deliveredRepository;
        this.triggerOutboxRepository = triggerOutboxRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata
    ) {
        createRunning(runId, sessionId, trigger, traceId, metadata, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRunning(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            String traceId,
            AgentRunMetadata metadata,
            UUID triggerOutboxId
    ) {
        Instant now = clock.instant();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(runId);
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(sessionId);
        entity.setTrigger(trigger);
        entity.setStatus(AgentRunStatus.RUNNING);
        entity.setVisibility(AgentRunVisibility.PRODUCTION);
        entity.setTriggerOutboxId(triggerOutboxId);
        copyTriggerCorrelation(entity, triggerOutboxId);
        entity.setTraceId(traceId);
        entity.setStartedAt(now);
        applyMetadata(entity, metadata);
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendStateSnapshot(UUID runId, FishingSessionState state) {
        AgentRunEntity entity = requireRun(runId);
        entity.setStateSnapshot(asMap(state));
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendContextSnapshot(UUID runId, FishingAgentContext context) {
        AgentRunEntity entity = requireRun(runId);
        entity.setContextSnapshot(asMap(context));
        if (context != null) {
            entity.setMemoryRefIds(context.retrievedMemoryIds());
        }
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendToolCall(UUID runId, ToolCallRecord toolCall) {
        requireRun(runId);
        AgentToolCallEntity entity = new AgentToolCallEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setRunId(runId);
        entity.setToolName(toolCall.request() == null ? null : toolCall.request().toolName());
        entity.setRequest(asMap(toolCall.request()));
        entity.setResult(asMap(toolCall.result()));
        entity.setLatencyMs(toolCall.latencyMs());
        entity.setObservedAt(toolCall.observedAt() == null ? clock.instant() : toolCall.observedAt());
        toolCallRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendCandidate(UUID runId, CandidateDecision candidate) {
        requireRun(runId);
        AgentCandidateDecisionEntity entity = new AgentCandidateDecisionEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setRunId(runId);
        entity.setDecision(asMap(candidate));
        candidateRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendValidation(UUID runId, DecisionValidationResult validation) {
        requireRun(runId);
        AgentValidationEntity entity = new AgentValidationEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setRunId(runId);
        entity.setResult(asMap(validation));
        validationRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMetadata(UUID runId, AgentRunMetadata metadata) {
        AgentRunEntity entity = requireRun(runId);
        applyMutableMetadata(entity, metadata);
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalize(UUID runId, AgentRunStatus status, DeliveredDecision delivered) {
        if (status == null || status == AgentRunStatus.RUNNING) {
            throw new IllegalArgumentException("finalize requires a terminal AgentRunStatus");
        }
        AgentRunEntity entity = requireRun(runId);
        entity.setStatus(status);
        entity.setFinishedAt(clock.instant());
        if (delivered != null) {
            entity.setDecisionId(delivered.decisionId());
            entity.setFallbackReason(delivered.fallbackReason());
            AgentDeliveredDecisionEntity row = new AgentDeliveredDecisionEntity();
            row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            row.setRunId(runId);
            row.setDecision(asMap(delivered));
            row.setFallbackUsed(delivered.fallbackUsed());
            row.setFallbackReason(delivered.fallbackReason());
            row.setSystemConfidence(delivered.systemConfidence() == null
                    ? null
                    : BigDecimal.valueOf(delivered.systemConfidence()));
            deliveredRepository.saveAndFlush(row);
        }
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeSkipped(UUID runId, AgentRunStatus status, String fallbackReason) {
        if (status == null || status == AgentRunStatus.RUNNING) {
            throw new IllegalArgumentException("finalizeSkipped requires a terminal AgentRunStatus");
        }
        AgentRunEntity entity = requireRun(runId);
        entity.setStatus(status);
        entity.setFinishedAt(clock.instant());
        entity.setFallbackReason(fallbackReason);
        agentRunRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveredDecision> current(UUID sessionId) {
        for (AgentRunEntity run : agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                sessionId, AgentRunVisibility.PRODUCTION
        )) {
            Optional<DeliveredDecision> delivered = deliveredRepository.findFirstByRunId(run.getId())
                    .map(row -> GuidanceContracts.mapper().convertValue(row.getDecision(), DeliveredDecision.class));
            if (delivered.isPresent()) {
                return delivered;
            }
        }
        return Optional.empty();
    }

    private void copyTriggerCorrelation(AgentRunEntity entity, UUID triggerOutboxId) {
        if (triggerOutboxId == null) {
            entity.setRelatedTriggers(List.of());
            entity.setTriggerReasonCodes(List.of());
            return;
        }
        GuidanceTriggerOutboxEntity outbox = triggerOutboxRepository.findById(triggerOutboxId).orElse(null);
        if (outbox == null) {
            entity.setRelatedTriggers(List.of());
            entity.setTriggerReasonCodes(List.of());
            return;
        }
        entity.setRelatedTriggers(outbox.getRelatedTriggers() == null
                ? List.of()
                : List.copyOf(outbox.getRelatedTriggers()));
        entity.setTriggerReasonCodes(outbox.getReasonCodes() == null
                ? List.of()
                : List.copyOf(outbox.getReasonCodes()));
    }

    private AgentRunEntity requireRun(UUID runId) {
        return agentRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("agent run not found: " + runId));
    }

    private static void applyMetadata(AgentRunEntity entity, AgentRunMetadata metadata) {
        if (metadata == null) {
            return;
        }
        entity.setModelProvider(metadata.modelProvider());
        entity.setModelName(metadata.modelName());
        entity.setModelVersion(metadata.modelVersion());
        entity.setPromptVersion(metadata.promptVersion());
        entity.setToolSchemaVersion(metadata.toolSchemaVersion());
        entity.setContextVersion(metadata.contextVersion());
        entity.setAgentPolicyVersion(metadata.agentPolicyVersion());
        entity.setLearningAlgorithmVersion(metadata.learningAlgorithmVersion());
        entity.setLearningSnapshotVersion(metadata.learningSnapshotVersion());
        applyMutableMetadata(entity, metadata);
    }

    /**
     * Later metadata updates may set decision/plan ids. Policy and component
     * version fields stay as stamped at {@code createRunning}.
     */
    private static void applyMutableMetadata(AgentRunEntity entity, AgentRunMetadata metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.guidancePlanVersion() != null) {
            entity.setGuidancePlanVersion(metadata.guidancePlanVersion());
        }
        if (metadata.decisionId() != null) {
            entity.setDecisionId(metadata.decisionId());
        }
    }

    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return GuidanceContracts.mapper().convertValue(value, MAP);
    }
}
