package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.versions.ResolvedAgentPolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable replay input for {@code FishingAgentRuntime.execute}.
 * Production callers must not supply authoritative state; they use the facade.
 * {@code context} may be null when safety BLOCK skipped context build.
 * {@code runDeadline} is the hard wall-clock for the whole run; null means
 * the runtime applies {@code app.guidance.runTimeoutMs} from execute start.
 * {@code policy} is bound once at run start; the runtime must use it rather
 * than re-reading {@code GuidanceProperties} version labels.
 */
public record AgentRunSnapshot(
        UUID runId,
        UUID sessionId,
        GuidanceTrigger trigger,
        EnvironmentSnapshot environment,
        FishingSessionState state,
        FishingAgentContext context,
        OptionalUserInput userInput,
        String traceId,
        Instant runDeadline,
        ResolvedAgentPolicy policy
) {
    public AgentRunSnapshot(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            EnvironmentSnapshot environment,
            FishingSessionState state,
            FishingAgentContext context,
            OptionalUserInput userInput,
            String traceId,
            Instant runDeadline
    ) {
        this(runId, sessionId, trigger, environment, state, context, userInput, traceId, runDeadline, null);
    }

    public AgentRunSnapshot(
            UUID runId,
            UUID sessionId,
            GuidanceTrigger trigger,
            EnvironmentSnapshot environment,
            FishingSessionState state,
            FishingAgentContext context,
            OptionalUserInput userInput,
            String traceId
    ) {
        this(runId, sessionId, trigger, environment, state, context, userInput, traceId, null, null);
    }

    public AgentRunSnapshot withContext(FishingAgentContext context) {
        return new AgentRunSnapshot(
                runId, sessionId, trigger, environment, state, context, userInput, traceId, runDeadline, policy
        );
    }
}
