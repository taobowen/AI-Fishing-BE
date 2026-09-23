package com.aifishing.guidance.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frozen replay input loaded from persisted {@code agent_runs}.
 * {@code context} may be null when safety BLOCK skipped context build.
 */
public record FrozenAgentRunSnapshot(
        String schemaVersion,
        UUID runId,
        UUID sessionId,
        GuidanceTrigger trigger,
        FishingSessionState state,
        FishingAgentContext context,
        RetrievedMemory retrievedMemory,
        List<ToolCallRecord> recordedToolObservations,
        Instant recordedClock,
        EvalComponentVersions componentVersions
) {
    public FrozenAgentRunSnapshot {
        recordedToolObservations = List.copyOf(
                recordedToolObservations == null ? List.of() : recordedToolObservations
        );
    }
}
