package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.ToolCallRecord;

import java.util.List;

/**
 * Outcome of one reasoning-round of tool execution. D must not start the next
 * model turn until this returns — rounds are serial.
 */
public record ToolRoundResult(
        List<ToolCallRecord> records,
        boolean executed,
        SkipReason skipReason
) {

    public ToolRoundResult {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static ToolRoundResult executed(List<ToolCallRecord> records) {
        return new ToolRoundResult(records, true, null);
    }

    public static ToolRoundResult skipped(SkipReason reason) {
        return new ToolRoundResult(List.of(), false, reason);
    }

    public enum SkipReason {
        MAX_TOOL_ROUNDS,
        MAX_TOOL_CALLS
    }
}
