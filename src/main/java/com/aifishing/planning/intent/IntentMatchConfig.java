package com.aifishing.planning.intent;

import com.aifishing.planning.PlanningProperties;

/**
 * Cache key for a spatial shortlist. Changing any value makes stored rows stale.
 */
public record IntentMatchConfig(
        String matchingVersion,
        double templatePointRadiusM,
        double templatePathCorridorM,
        double requiredPointRadiusM,
        int maxMatches
) {
    public static IntentMatchConfig from(PlanningProperties.Spatial spatial) {
        return new IntentMatchConfig(
                spatial.getIntentMatchingVersion(),
                spatial.getIntentTemplatePointRadiusM(),
                spatial.getIntentTemplatePathCorridorM(),
                spatial.getIntentRequiredPointRadiusM(),
                spatial.getIntentMaxOpportunitiesPerIntent()
        );
    }
}
