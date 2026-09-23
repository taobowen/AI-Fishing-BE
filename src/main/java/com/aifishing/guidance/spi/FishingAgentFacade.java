package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.runtime.OptionalUserInput;

import java.util.UUID;

/**
 * Production entry. Callers must not pass authoritative {@code FishingSessionState}.
 */
public interface FishingAgentFacade {

    AgentRunResult run(UUID sessionId, GuidanceTrigger trigger, OptionalUserInput optionalUserInput);

    /**
     * Production entry from the trigger outbox. {@code triggerOutboxId} is persisted
     * on {@code agent_runs} for exact Trigger→AgentRun correlation. {@code USER_REQUEST}
     * callers pass {@code null}.
     */
    default AgentRunResult run(
            UUID sessionId,
            GuidanceTrigger trigger,
            OptionalUserInput optionalUserInput,
            UUID triggerOutboxId
    ) {
        return run(sessionId, trigger, optionalUserInput);
    }
}
