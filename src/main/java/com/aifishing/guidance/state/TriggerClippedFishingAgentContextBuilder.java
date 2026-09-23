package com.aifishing.guidance.state;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.spi.FishingAgentContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Clips the already-built immutable state by trigger. recentHistory comes from
 * session clips plus retrieved explicit/inferred memory.
 */
@Component
public class TriggerClippedFishingAgentContextBuilder implements FishingAgentContextBuilder {

    private final int recentHistoryLimit;

    public TriggerClippedFishingAgentContextBuilder() {
        this.recentHistoryLimit = 6;
    }

    @Autowired
    public TriggerClippedFishingAgentContextBuilder(GuidanceProperties properties) {
        this.recentHistoryLimit = properties.getMemory().getRecentHistoryLimit();
    }

    @Override
    public FishingAgentContext build(
            FishingSessionState state,
            GuidanceTrigger trigger,
            RetrievedMemory retrievedMemory
    ) {
        FishingSessionState.Fishing fishing = state.fishing();
        FishingSessionState.Environment environment = state.environment();
        RetrievedMemory memory = retrievedMemory == null ? RetrievedMemory.empty() : retrievedMemory;
        List<OriginalPlanStep> originalPlanSteps = state.plan() == null
                ? List.of()
                : state.plan().originalPlanSteps();
        return new FishingAgentContext(
                GuidanceSchemaVersion.VALUE,
                trigger,
                state.session() == null ? null : state.session().targetSpecies(),
                clipSituation(fishing, trigger),
                new FishingAgentContext.ContextWeather(
                        environment == null ? null : environment.windDirection(),
                        environment == null ? null : environment.windSpeedKph(),
                        environment == null ? null : environment.weatherAgeMinutes()
                ),
                RecentHistoryClipper.clip(state, trigger, memory, recentHistoryLimit),
                memory.userPreferences(),
                memory.inferredPreferences().isEmpty() ? null : memory.inferredPreferences(),
                memory.retrievedMemoryIds().isEmpty() ? null : memory.retrievedMemoryIds(),
                originalPlanSteps
        );
    }

    private static FishingAgentContext.CurrentSituation clipSituation(
            FishingSessionState.Fishing fishing,
            GuidanceTrigger trigger
    ) {
        if (fishing == null) {
            return new FishingAgentContext.CurrentSituation(null, null, null, null, null, null, null, null);
        }
        return switch (trigger) {
            case NO_BITE_THRESHOLD, CONSECUTIVE_FAILURE, REPEATED_BITE_PATTERN -> withAdHocIfOpen(
                    fishing,
                    new FishingAgentContext.CurrentSituation(
                            fishing.currentTripWaypointId(),
                            fishing.currentSessionWaypointProgressId(),
                            fishing.structureType(),
                            fishing.timeAtWaypointMinutes(),
                            fishing.noBiteMinutes(),
                            fishing.lureFamily(),
                            fishing.depthMinM(),
                            fishing.depthMaxM()
                    )
            );
            case FISH_ON -> withAdHocIfOpen(
                    fishing,
                    new FishingAgentContext.CurrentSituation(
                            fishing.currentTripWaypointId(),
                            fishing.currentSessionWaypointProgressId(),
                            fishing.structureType(),
                            null,
                            null,
                            fishing.lureFamily(),
                            fishing.depthMinM(),
                            fishing.depthMaxM()
                    )
            );
            case WAYPOINT_REACHED, PLAN_STEP_COMPLETED -> new FishingAgentContext.CurrentSituation(
                    fishing.currentTripWaypointId(),
                    fishing.currentSessionWaypointProgressId(),
                    fishing.structureType(),
                    fishing.timeAtWaypointMinutes(),
                    null,
                    null,
                    fishing.depthMinM(),
                    fishing.depthMaxM()
            );
            case SIGNIFICANT_WEATHER_CHANGE, SAFETY_STATE_CHANGED, RETURN_RISK_CHANGED ->
                    new FishingAgentContext.CurrentSituation(
                            fishing.currentTripWaypointId(),
                            fishing.currentSessionWaypointProgressId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
            case SIGNIFICANT_LOCATION_CHANGE, ROUTE_DEVIATION -> new FishingAgentContext.CurrentSituation(
                    fishing.currentTripWaypointId(),
                    fishing.currentSessionWaypointProgressId(),
                    fishing.structureType(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case USER_REQUEST -> new FishingAgentContext.CurrentSituation(
                    fishing.currentTripWaypointId(),
                    fishing.currentSessionWaypointProgressId(),
                    fishing.structureType(),
                    fishing.timeAtWaypointMinutes(),
                    fishing.noBiteMinutes(),
                    fishing.lureFamily(),
                    fishing.depthMinM(),
                    fishing.depthMaxM()
            );
            case USER_STARTED_AD_HOC_FISHING, USER_ENDED_AD_HOC_FISHING -> new FishingAgentContext.CurrentSituation(
                    fishing.currentTripWaypointId(),
                    fishing.currentSessionWaypointProgressId(),
                    fishing.structureType(),
                    fishing.timeAtWaypointMinutes(),
                    fishing.noBiteMinutes(),
                    fishing.lureFamily(),
                    fishing.depthMinM(),
                    fishing.depthMaxM(),
                    fishing.activityState(),
                    fishing.activityStateSource(),
                    fishing.adHocFishingStopId(),
                    fishing.adHocStartedAt(),
                    fishing.fishingTargetId(),
                    fishing.physicalZoneId(),
                    fishing.lakeFeatureId()
            );
        };
    }

    private static FishingAgentContext.CurrentSituation withAdHocIfOpen(
            FishingSessionState.Fishing fishing,
            FishingAgentContext.CurrentSituation base
    ) {
        if (fishing.activityStateSource() != ActivityStateSource.USER_AD_HOC) {
            return base;
        }
        return new FishingAgentContext.CurrentSituation(
                base.tripWaypointId(),
                base.sessionWaypointProgressId(),
                base.structure(),
                base.timeAtWaypointMinutes(),
                base.noBiteMinutes(),
                base.lureFamily(),
                base.depthMinM(),
                base.depthMaxM(),
                fishing.activityState(),
                fishing.activityStateSource(),
                fishing.adHocFishingStopId(),
                fishing.adHocStartedAt(),
                fishing.fishingTargetId(),
                fishing.physicalZoneId(),
                fishing.lakeFeatureId()
        );
    }
}
