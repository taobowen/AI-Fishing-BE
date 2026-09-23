package com.aifishing.guidance.contracts;

import com.aifishing.guidance.state.TriggerClippedFishingAgentContextBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.TRIP_WAYPOINT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalPlanStepsSerializationTest {

    @Test
    void modelInputKeepsOriginalPlanStepsAfterShortHorizonReplacement() throws Exception {
        List<UUID> ids = List.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000004"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000005"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000006")
        );
        List<HorizonStep> shortHorizon = List.of(
                new HorizonStep(1, GuidanceAction.STAY, ids.get(0), 20, true),
                new HorizonStep(2, GuidanceAction.MOVE, ids.get(1), null, false),
                new HorizonStep(3, GuidanceAction.MOVE, ids.get(2), null, false),
                new HorizonStep(4, GuidanceAction.MOVE, ids.get(3), null, false)
        );
        List<OriginalPlanStep> original = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            original.add(new OriginalPlanStep(
                    i + 1,
                    ids.get(i),
                    i == 0 || i == 2 ? ids.get(0) : null,
                    null,
                    null,
                    i == 0 ? List.of(UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1")) : List.of(),
                    i == 0 ? "FISHING" : "UPCOMING"
            ));
        }
        FishingSessionState sessionState = state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90,
                ids.get(0), shortHorizon, original
        );
        FishingAgentContext context = new TriggerClippedFishingAgentContextBuilder()
                .build(sessionState, GuidanceTrigger.NO_BITE_THRESHOLD, RetrievedMemory.empty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger", GuidanceTrigger.NO_BITE_THRESHOLD);
        payload.put("state", sessionState);
        payload.put("context", context);
        JsonNode json = GuidanceContracts.mapper().valueToTree(payload);

        assertThat(json.path("state").path("plan").path("shortHorizonSteps")).hasSize(4);
        assertThat(json.path("state").path("plan").path("originalPlanSteps")).hasSize(6);
        assertThat(json.path("context").path("originalPlanSteps")).hasSize(6);
        for (UUID id : ids) {
            assertThat(json.path("state").path("plan").path("originalPlanSteps").toString()).contains(id.toString());
            assertThat(json.path("context").path("originalPlanSteps").toString()).contains(id.toString());
        }
        assertThat(json.path("state").path("plan").path("shortHorizonSteps").toString())
                .doesNotContain(ids.get(5).toString());
        assertThat(context.originalPlanSteps()).isEqualTo(sessionState.plan().originalPlanSteps());
    }

    @Test
    void missingOriginalPlanStepsDeserializesToEmptyList() throws Exception {
        String oldPlan = """
                {"originalTripPlanId":"%s","currentGuidancePlanVersion":1,"currentStep":1,"shortHorizonSteps":[]}
                """.formatted(TRIP_WAYPOINT);
        FishingSessionState.Plan plan = GuidanceContracts.mapper().readValue(oldPlan, FishingSessionState.Plan.class);
        assertThat(plan.originalPlanSteps()).isEmpty();
        assertThat(plan.activeGuidanceTargetTripWaypointId()).isNull();

        String oldContext = """
                {
                  "schemaVersion":"guidance.contracts.v1",
                  "trigger":"USER_REQUEST",
                  "currentSituation":{},
                  "weather":{},
                  "recentHistory":[]
                }
                """;
        FishingAgentContext context = GuidanceContracts.mapper().readValue(oldContext, FishingAgentContext.class);
        assertThat(context.originalPlanSteps()).isEmpty();
        assertThat(context.currentSituation().adHocFishingStopId()).isNull();
        assertThat(context.currentSituation().adHocStartedAt()).isNull();
        assertThat(context.currentSituation().fishingTargetId()).isNull();
        assertThat(context.currentSituation().physicalZoneId()).isNull();
        assertThat(context.currentSituation().lakeFeatureId()).isNull();
        assertThat(context.currentSituation().activityState()).isNull();
        assertThat(context.currentSituation().activityStateSource()).isNull();
    }

    @Test
    void missingAdHocFishingFieldsDeserializeAbsent() throws Exception {
        String oldFishing = """
                {
                  "currentTripWaypointId":"%s",
                  "currentSessionWaypointProgressId":"%s",
                  "structureType":"POINT",
                  "activityState":"FISHING",
                  "activityStateSource":"PROGRESS",
                  "noBiteMinutes":31
                }
                """.formatted(TRIP_WAYPOINT, TRIP_WAYPOINT);
        FishingSessionState.Fishing fishing = GuidanceContracts.mapper()
                .readValue(oldFishing, FishingSessionState.Fishing.class);
        assertThat(fishing.currentTripWaypointId()).isEqualTo(TRIP_WAYPOINT);
        assertThat(fishing.adHocFishingStopId()).isNull();
        assertThat(fishing.adHocStartedAt()).isNull();
        assertThat(fishing.fishingTargetId()).isNull();
        assertThat(fishing.physicalZoneId()).isNull();
        assertThat(fishing.lakeFeatureId()).isNull();
    }
}
