package com.aifishing.guidance.attribution;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.runtime.GuidanceFallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AdviceLifecycleWriter {

    private static final Logger log = LoggerFactory.getLogger(AdviceLifecycleWriter.class);

    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final SessionEventWriter sessionEventWriter;
    private final GuidanceLearningHooks learningHooks;
    private final Clock clock;

    public AdviceLifecycleWriter(
            AgentDeliveredDecisionRepository deliveredRepository,
            SessionEventWriter sessionEventWriter,
            GuidanceLearningHooks learningHooks,
            Clock clock
    ) {
        this.deliveredRepository = deliveredRepository;
        this.sessionEventWriter = sessionEventWriter;
        this.learningHooks = learningHooks;
        this.clock = clock;
    }

    public void writeCreated(UUID fishingSessionId, AgentRunResult result) {
        if (fishingSessionId == null || result == null || result.delivered() == null) {
            return;
        }
        writeCreated(fishingSessionId, result.delivered());
    }

    public void writeCreated(UUID fishingSessionId, DeliveredDecision delivered) {
        if (fishingSessionId == null || delivered == null || GuidanceFallback.isKillSwitch(delivered)) {
            return;
        }
        try {
            AgentDeliveredDecisionEntity entity = findDelivered(delivered);
            if (entity == null) {
                log.warn("Delivered decision row missing for session {}", fishingSessionId);
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("deliveredDecisionId", entity.getId().toString());
            if (delivered.decisionId() != null) {
                payload.put("decisionId", delivered.decisionId().toString());
            }
            payload.put("primaryAction", delivered.primaryAction().name());
            if (delivered.secondaryAction() != null) {
                payload.put("secondaryAction", delivered.secondaryAction().name());
            }
            sessionEventWriter.writeAudit(fishingSessionId, new SessionEvent(
                    GuidanceSchemaVersion.VALUE,
                    SessionEventType.ADVICE_CREATED,
                    clock.instant(),
                    EventSource.SERVER,
                    "advice-created:" + entity.getId(),
                    payload,
                    null
            ));
            learningHooks.onAdviceDelivered(fishingSessionId, delivered, entity);
        } catch (RuntimeException ex) {
            log.warn("Failed to write ADVICE_CREATED for session {}: {}", fishingSessionId, ex.getMessage());
        }
    }

    public AgentDeliveredDecisionEntity findDelivered(DeliveredDecision delivered) {
        if (delivered.decisionId() != null) {
            AgentDeliveredDecisionEntity byRun = deliveredRepository.findFirstByRunId(delivered.decisionId()).orElse(null);
            if (byRun != null) {
                return byRun;
            }
        }
        return null;
    }
}
