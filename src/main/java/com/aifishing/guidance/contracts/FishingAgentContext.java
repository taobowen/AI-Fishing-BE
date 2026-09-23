package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishingAgentContext(
        String schemaVersion,
        GuidanceTrigger trigger,
        FishSpecies targetSpecies,
        CurrentSituation currentSituation,
        ContextWeather weather,
        List<String> recentHistory,
        UserFishingPreferences userPreferences,
        List<InferredUserPreference> inferredPreferences,
        List<String> retrievedMemoryIds,
        List<OriginalPlanStep> originalPlanSteps
) {
    public FishingAgentContext {
        originalPlanSteps = originalPlanSteps == null ? List.of() : List.copyOf(originalPlanSteps);
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public FishingAgentContext(
            String schemaVersion,
            GuidanceTrigger trigger,
            FishSpecies targetSpecies,
            CurrentSituation currentSituation,
            ContextWeather weather,
            List<String> recentHistory,
            UserFishingPreferences userPreferences,
            List<InferredUserPreference> inferredPreferences,
            List<String> retrievedMemoryIds
    ) {
        this(
                schemaVersion,
                trigger,
                targetSpecies,
                currentSituation,
                weather,
                recentHistory,
                userPreferences,
                inferredPreferences,
                retrievedMemoryIds,
                List.of()
        );
    }

    public record CurrentSituation(
            UUID tripWaypointId,
            UUID sessionWaypointProgressId,
            FeatureType structure,
            Integer timeAtWaypointMinutes,
            Integer noBiteMinutes,
            LureFamily lureFamily,
            Double depthMinM,
            Double depthMaxM,
            FishingActivityState activityState,
            ActivityStateSource activityStateSource,
            UUID adHocFishingStopId,
            Instant adHocStartedAt,
            UUID fishingTargetId,
            UUID physicalZoneId,
            UUID lakeFeatureId
    ) {
        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public CurrentSituation(
                UUID tripWaypointId,
                UUID sessionWaypointProgressId,
                FeatureType structure,
                Integer timeAtWaypointMinutes,
                Integer noBiteMinutes,
                LureFamily lureFamily,
                Double depthMinM,
                Double depthMaxM
        ) {
            this(
                    tripWaypointId,
                    sessionWaypointProgressId,
                    structure,
                    timeAtWaypointMinutes,
                    noBiteMinutes,
                    lureFamily,
                    depthMinM,
                    depthMaxM,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record ContextWeather(
            CompassDirection windDirection,
            Double windSpeedKph,
            Integer ageMinutes
    ) {
    }
}
