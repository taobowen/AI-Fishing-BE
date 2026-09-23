package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.EvalExecutionMode;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.ReplayMode;

/**
 * Side-effect-free eval entry beside {@link FishingAgentFacade}.
 * Implementations must use {@link EvalExecutionMode#EVAL} and a read-only
 * {@link EvalToolRegistry}. They must not persist a session
 * {@code DeliveredDecision} or enqueue live triggers.
 */
public interface EvalRuntime {

    default EvalExecutionMode executionMode() {
        return EvalExecutionMode.EVAL;
    }

    AgentRunResult execute(FrozenAgentRunSnapshot snapshot, ReplayMode replayMode);

    /**
     * Frozen version-compare entry. Default implementations ignore {@code policyVersion}.
     * {@link com.aifishing.guidance.eval.DefaultEvalRuntime} resolves the catalog version
     * and overlays eval tools so live {@code historical_performance} is not hit.
     */
    default AgentRunResult execute(
            FrozenAgentRunSnapshot snapshot,
            ReplayMode replayMode,
            String policyVersion
    ) {
        return execute(snapshot, replayMode);
    }
}
