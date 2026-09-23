package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.EvalExecutionMode;

/**
 * Read-only tool registry for {@link EvalExecutionMode#EVAL}.
 * Live activity / historical tools may expose recorded copies.
 * Write-path tools must be absent or return {@code UNKNOWN}.
 */
public interface EvalToolRegistry extends AgentToolRegistry {

    default EvalExecutionMode executionMode() {
        return EvalExecutionMode.EVAL;
    }
}
