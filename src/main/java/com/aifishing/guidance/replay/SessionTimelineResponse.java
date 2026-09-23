package com.aifishing.guidance.replay;

import java.util.List;

public record SessionTimelineResponse(
        SessionReplaySummary summary,
        List<SessionTimelineEvent> events,
        List<OriginalPlanStop> originalPlan,
        List<HorizonPlanStep> latestShortHorizon,
        List<ActualProgressStop> actualProgress
) {
}
