package com.aifishing.guidance.state;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.InferredUserPreference;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.UserFishingPreferences;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.lake.processing.dto.FeatureType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerClippedFishingAgentContextBuilderTest {

    private static final Instant AT = Instant.parse("2026-09-16T13:33:00Z");
    private static final UUID SESSION = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TRIP_WP = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private static final UUID PROGRESS = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    private final TriggerClippedFishingAgentContextBuilder builder = new TriggerClippedFishingAgentContextBuilder();

    @Test
    void memoryFieldsStayEmptyAndNoBiteKeepsSituation() {
        FishingSessionState state = state();

        FishingAgentContext context = builder.build(state, GuidanceTrigger.NO_BITE_THRESHOLD, RetrievedMemory.empty());

        assertThat(context.trigger()).isEqualTo(GuidanceTrigger.NO_BITE_THRESHOLD);
        assertThat(context.recentHistory()).containsExactly("No bites for 31 minutes");
        assertThat(context.userPreferences()).isNull();
        assertThat(context.inferredPreferences()).isNull();
        assertThat(context.retrievedMemoryIds()).isNull();
        assertThat(context.currentSituation().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(context.currentSituation().sessionWaypointProgressId()).isEqualTo(PROGRESS);
        assertThat(context.currentSituation().noBiteMinutes()).isEqualTo(31);
        assertThat(context.weather().ageMinutes()).isEqualTo(4);
        assertThat(context.weather().windSpeedKph()).isEqualTo(16.0);
        assertThat(context.originalPlanSteps()).hasSize(1);
        assertThat(context.originalPlanSteps().getFirst().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(context.originalPlanSteps().getFirst().packageMemberIds()).containsExactly(TRIP_WP);
    }

    @Test
    void adHocTriggersKeepRichSituationAndAdHocFields() {
        UUID stopId = UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8");
        UUID targetId = UUID.fromString("aba7b810-9dad-11d1-80b4-00c04fd430c8");
        FishingSessionState base = state();
        FishingSessionState.Fishing fishing = new FishingSessionState.Fishing(
                base.fishing().currentTripWaypointId(),
                base.fishing().currentSessionWaypointProgressId(),
                base.fishing().structureType(),
                base.fishing().depthMinM(),
                base.fishing().depthMaxM(),
                base.fishing().lureFamily(),
                base.fishing().presentation(),
                base.fishing().retrieveStyle(),
                base.fishing().timeAtWaypointMinutes(),
                FishingActivityState.FISHING,
                AT,
                ActivityStateSource.USER_AD_HOC,
                31,
                31,
                stopId,
                AT,
                targetId,
                SESSION,
                PROGRESS
        );
        FishingSessionState state = new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                fishing,
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );

        FishingAgentContext context = builder.build(state, GuidanceTrigger.USER_STARTED_AD_HOC_FISHING, RetrievedMemory.empty());

        assertThat(context.currentSituation().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(context.currentSituation().lureFamily()).isEqualTo(LureFamily.JERKBAIT);
        assertThat(context.currentSituation().noBiteMinutes()).isEqualTo(31);
        assertThat(context.currentSituation().activityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(context.currentSituation().activityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        assertThat(context.currentSituation().adHocFishingStopId()).isEqualTo(stopId);
        assertThat(context.currentSituation().adHocStartedAt()).isEqualTo(AT);
        assertThat(context.currentSituation().fishingTargetId()).isEqualTo(targetId);
        assertThat(context.currentSituation().physicalZoneId()).isEqualTo(SESSION);
        assertThat(context.currentSituation().lakeFeatureId()).isEqualTo(PROGRESS);
        assertThat(context.originalPlanSteps()).hasSize(1);
    }

    @Test
    void fishOnAndNoBiteDuringAdHocKeepActivityAndStopIds() {
        UUID stopId = UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8");
        FishingSessionState base = state();
        FishingSessionState.Fishing fishing = new FishingSessionState.Fishing(
                base.fishing().currentTripWaypointId(),
                base.fishing().currentSessionWaypointProgressId(),
                base.fishing().structureType(),
                base.fishing().depthMinM(),
                base.fishing().depthMaxM(),
                base.fishing().lureFamily(),
                base.fishing().presentation(),
                base.fishing().retrieveStyle(),
                base.fishing().timeAtWaypointMinutes(),
                FishingActivityState.FISHING,
                AT,
                ActivityStateSource.USER_AD_HOC,
                31,
                31,
                stopId,
                AT,
                null,
                null,
                null
        );
        FishingSessionState state = new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                fishing,
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );

        FishingAgentContext fishOn = builder.build(state, GuidanceTrigger.FISH_ON, RetrievedMemory.empty());
        FishingAgentContext noBite = builder.build(state, GuidanceTrigger.NO_BITE_THRESHOLD, RetrievedMemory.empty());

        assertThat(fishOn.currentSituation().activityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        assertThat(fishOn.currentSituation().adHocFishingStopId()).isEqualTo(stopId);
        assertThat(noBite.currentSituation().activityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        assertThat(noBite.currentSituation().adHocFishingStopId()).isEqualTo(stopId);
        assertThat(noBite.currentSituation().noBiteMinutes()).isEqualTo(31);
    }

    @Test
    void safetyTriggerClipsSituationToWaypointIdentity() {
        FishingSessionState state = state();

        FishingAgentContext context = builder.build(state, GuidanceTrigger.SAFETY_STATE_CHANGED, RetrievedMemory.empty());

        assertThat(context.recentHistory()).isEmpty();
        assertThat(context.currentSituation().tripWaypointId()).isEqualTo(TRIP_WP);
        assertThat(context.currentSituation().sessionWaypointProgressId()).isEqualTo(PROGRESS);
        assertThat(context.currentSituation().lureFamily()).isNull();
        assertThat(context.currentSituation().noBiteMinutes()).isNull();
        assertThat(context.weather().windDirection()).isEqualTo(CompassDirection.W);
    }

    @Test
    void recentHistoryClipsSummariesAndOverThresholdInferred() {
        FishingSessionState state = stateWithSummaries(List.of("Changed lure to JERKBAIT", "Fish on"));
        RetrievedMemory memory = new RetrievedMemory(
                new UserFishingPreferences(
                        GuidanceSchemaVersion.VALUE, SESSION, true, List.of(), List.of(), 500.0, 0.4
                ),
                List.of(new InferredUserPreference(
                        GuidanceSchemaVersion.VALUE, SESSION, "stayPreferred", "true", 3, 0.63, AT, AT
                )),
                List.of("pref:explicit:" + SESSION, "pref:inferred:" + SESSION + ":stayPreferred")
        );

        FishingAgentContext context = builder.build(state, GuidanceTrigger.NO_BITE_THRESHOLD, memory);

        assertThat(context.recentHistory()).contains(
                "Changed lure to JERKBAIT",
                "Fish on",
                "Inferred stayPreferred=true (evidence 3)",
                "Explicit prefs: maxMoveMeters=500.0, avoidLongMoveInWind=true, windConservatism=0.4"
        );
        assertThat(context.retrievedMemoryIds()).contains("pref:explicit:" + SESSION);
        assertThat(context.inferredPreferences()).hasSize(1);
        assertThat(context.userPreferences().maxMoveMeters()).isEqualTo(500.0);
    }

    private static FishingSessionState state() {
        return new FishingSessionState(
                GuidanceSchemaVersion.VALUE,
                new FishingSessionState.Session(
                        SESSION, PROGRESS, FishingSessionStatus.ACTIVE, AT, FishSpecies.SMALLMOUTH_BASS, 90
                ),
                new FishingSessionState.Position(44.75, -78.92, 4.5, 1.2, 210.0),
                new FishingSessionState.Boat(BoatType.FISHING_BOAT, PropulsionType.GAS_OUTBOARD, 8.0, 12000.0, 1500.0),
                new FishingSessionState.Fishing(
                        TRIP_WP, PROGRESS, FeatureType.POINT, 3.0, 4.5, LureFamily.JERKBAIT,
                        PresentationTechnique.TWITCH_PAUSE, RetrieveStyle.SLOW, 31,
                        FishingActivityState.FISHING, AT, ActivityStateSource.PROGRESS, 31, 31
                ),
                new FishingSessionState.Environment(
                        WeatherCondition.CLOUDY, 16.0, CompassDirection.W, 1012.0, 18.0, AT, 4
                ),
                new FishingSessionState.Recent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new FishingSessionState.Performance(0, 0.0, 31, 0),
                new FishingSessionState.Plan(
                        SESSION,
                        1,
                        1,
                        List.of(new HorizonStep(1, GuidanceAction.STAY, TRIP_WP, 20, true)),
                        List.of(new OriginalPlanStep(1, TRIP_WP, SESSION, null, PROGRESS, List.of(TRIP_WP), "FISHING"))
                )
        );
    }

    private static FishingSessionState stateWithSummaries(List<String> summaries) {
        FishingSessionState base = state();
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                base.fishing(),
                base.environment(),
                new FishingSessionState.Recent(List.of(), List.of(), List.of(), List.of(), List.of(), summaries),
                base.performance(),
                base.plan()
        );
    }
}
