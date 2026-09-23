package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolCallRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral input for one model turn. OpenAI types must not appear here.
 */
public record ModelTurnInput(
        UUID runId,
        UUID decisionId,
        GuidanceTrigger trigger,
        FishingSessionState state,
        FishingAgentContext context,
        OptionalUserInput userInput,
        List<ToolCallRecord> observations,
        int turnIndex,
        Instant runDeadline,
        String promptInstructions
) {
    public ModelTurnInput {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public ModelTurnInput(
            UUID runId,
            UUID decisionId,
            GuidanceTrigger trigger,
            FishingSessionState state,
            FishingAgentContext context,
            OptionalUserInput userInput,
            List<ToolCallRecord> observations,
            int turnIndex,
            Instant runDeadline
    ) {
        this(runId, decisionId, trigger, state, context, userInput, observations, turnIndex, runDeadline, null);
    }
}
