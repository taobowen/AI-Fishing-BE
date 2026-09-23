package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.UsageTelemetry;

/**
 * Observed logical budget for one platform eval case. Token fields come from
 * {@link UsageTelemetry}; unknown usage stays null and is not treated as 0.
 */
public record EvalLogicalUsage(
        int modelTurns,
        int toolRounds,
        int toolCalls,
        UsageTelemetry telemetry,
        Long wallClockMs
) {
    public Integer observedTotalTokens() {
        if (telemetry == null) {
            return null;
        }
        if (telemetry.totalTokens() != null) {
            return telemetry.totalTokens();
        }
        if (telemetry.inputTokens() != null && telemetry.outputTokens() != null) {
            return telemetry.inputTokens() + telemetry.outputTokens();
        }
        return telemetry.inputTokens() != null ? telemetry.inputTokens() : telemetry.outputTokens();
    }
}
