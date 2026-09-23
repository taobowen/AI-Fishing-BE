package com.aifishing.guidance.replay;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.GuidanceTrigger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionTimelineEvent(
        UUID id,
        Instant occurredAt,
        TimelineSource source,
        String kind,
        TriggerDispatchStatus dispatchStatus,
        UUID runId,
        AgentRunVisibility visibility,
        AgentRunStatus runStatus,
        GuidanceTrigger trigger,
        Map<String, Object> detail
) {
}
