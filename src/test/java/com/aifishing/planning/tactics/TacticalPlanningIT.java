package com.aifishing.planning.tactics;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.LureLengthBand;
import com.aifishing.common.enums.LureWeightBand;
import com.aifishing.gear.domain.Gear;
import com.aifishing.gear.lure.LureProfile;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TacticalPlanningIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Test
    void zeroAndManyLuresProduceTheSameSpatialPlan() throws Exception {
        seedPlanLake();
        JsonNode withoutLures = generateShorePlan();
        saveLure(LureFamily.PADDLETAIL, LureLengthBand.THREE_TO_4_IN, null, List.of(LureColorFamily.NATURAL));
        saveLure(LureFamily.FROG, LureLengthBand.FOUR_TO_5_IN, null, List.of(LureColorFamily.BLACK));
        saveLure(LureFamily.SPOON, null, LureWeightBand.FROM_1_4_TO_3_8, List.of(LureColorFamily.SILVER));
        saveLure(LureFamily.JIG, null, LureWeightBand.FROM_3_8_TO_1_2, List.of(LureColorFamily.GREEN_PUMPKIN));
        saveLure(LureFamily.NED_RIG, LureLengthBand.UNDER_3_IN, null, List.of(LureColorFamily.GREEN_PUMPKIN));
        JsonNode withLures = generateShorePlan();

        assertThat(spatialSignature(withoutLures)).isEqualTo(spatialSignature(withLures));
        assertThat(withLures.path("plan").path("waypoints").get(0).path("tactical").path("idealTactic").isMissingNode())
                .isFalse();
    }

    @Test
    void emptyLockerStillReturnsIdealTactics() throws Exception {
        seedPlanLake();
        mockMvc.perform(generate(saveTripAndStrategy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.tacticalAlgorithmVersion", is(TacticalRecommendationService.ALGORITHM_VERSION)))
                .andExpect(jsonPath("$.plan.waypoints[0].tactical.bestLockerLureId").doesNotExist())
                .andExpect(jsonPath("$.plan.waypoints[0].tactical.idealTactic.lureFamily", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].tactical.showIdealOption", is(true)));
    }

    @Test
    void noRodNoLineWithLuresStillPlans() throws Exception {
        seedPlanLake();
        saveLure(LureFamily.PADDLETAIL, LureLengthBand.THREE_TO_4_IN, null, List.of(LureColorFamily.WHITE_PEARL));
        saveLure(LureFamily.NED_RIG, LureLengthBand.UNDER_3_IN, null, List.of(LureColorFamily.GREEN_PUMPKIN));
        mockMvc.perform(generate(saveTripAndStrategy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void noGearAtAllStillPlans() throws Exception {
        seedPlanLake();
        mockMvc.perform(generate(saveTripAndStrategy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void editingLockerDoesNotRewriteExistingPlanTactics() throws Exception {
        seedPlanLake();
        String first = mockMvc.perform(generate(saveTripAndStrategy()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode planA = objectMapper.readTree(first).path("plan");
        UUID tripId = UUID.fromString(planA.path("tripId").asText());
        String tacticalA = planA.path("waypoints").get(0).path("tactical").toString();

        Gear lure = saveLure(LureFamily.NED_RIG, LureLengthBand.UNDER_3_IN, null, List.of(LureColorFamily.GREEN_PUMPKIN));
        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(planA.path("id").asText())));
        String afterEdit = mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(afterEdit).path("waypoints").get(0).path("tactical").toString())
                .isEqualTo(tacticalA);

        UUID strategyB = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper)).getId();
        String second = mockMvc.perform(generate(tripId, strategyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tacticalB = objectMapper.readTree(second).path("plan").path("waypoints").get(0).path("tactical");
        assertThat(tacticalB.path("bestLockerLureId").asText()).isEqualTo(lure.getId().toString());
    }

    @Test
    void lureUpdateDoesNotChangePriorPlanWhenPatched() throws Exception {
        seedPlanLake();
        Gear lure = saveLure(LureFamily.NED_RIG, LureLengthBand.UNDER_3_IN, null, List.of(LureColorFamily.GREEN_PUMPKIN));
        PlannedTrip planned = saveTripAndStrategy();
        String first = mockMvc.perform(generate(planned))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tacticalA = objectMapper.readTree(first).path("plan").path("waypoints").get(0).path("tactical").toString();

        mockMvc.perform(asDev(patch("/api/v1/me/gear/" + lure.getId())).content("""
                {
                  "lureProfile": {
                    "lureFamily":"FROG",
                    "lengthBand":"4_TO_5_IN",
                    "colors":["BLACK"]
                  }
                }
                """))
                .andExpect(status().isOk());

        String current = mockMvc.perform(asDev(get("/api/v1/trips/" + planned.tripId() + "/plan")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(current).path("waypoints").get(0).path("tactical").toString())
                .isEqualTo(tacticalA);
    }

    private void seedPlanLake() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.86,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.002, 50),
                2.5,
                3.5,
                0.8,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    private JsonNode generateShorePlan() throws Exception {
        String body = mockMvc.perform(generate(saveTripAndStrategy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private String spatialSignature(JsonNode generateResponse) {
        StringBuilder out = new StringBuilder();
        for (JsonNode waypoint : generateResponse.path("plan").path("waypoints")) {
            out.append(waypoint.path("sequence").asInt()).append('|')
                    .append(waypoint.path("fishingTargetId").asText()).append('|')
                    .append(waypoint.path("zoneId").asText()).append('|')
                    .append(waypoint.path("visitScopeId").asText()).append('|')
                    .append(waypoint.path("targetKind").asText()).append('|')
                    .append(waypoint.path("plannedArrivalAt").asText()).append('|')
                    .append(waypoint.path("plannedDepartureAt").asText()).append('|')
                    .append(waypoint.path("selectedFishingPath").toString()).append(';');
        }
        return out.toString();
    }

    private Gear saveLure(
            LureFamily family,
            LureLengthBand length,
            LureWeightBand weight,
            List<LureColorFamily> colors
    ) {
        Gear gear = new Gear();
        gear.setUserId(DevSeedIds.USER_ID);
        gear.setType(GearType.LURE);
        LureProfile profile = new LureProfile(family, length, null, weight, null, colors, null);
        gear.setName(profile.autoName());
        gear.setMetadata(LureProfile.mergeInto(null, profile));
        gear.setActive(true);
        return gearRepository.save(gear);
    }

    private PlannedTrip saveTripAndStrategy() {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        UUID tripId = tripRepository.save(trip).getId();
        UUID strategyRunId = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper)).getId();
        return new PlannedTrip(tripId, strategyRunId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generate(PlannedTrip planned) {
        return generate(planned.tripId(), planned.strategyRunId());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generate(UUID tripId, UUID strategyRunId) {
        return asDev(post("/api/v1/trips/" + tripId + "/plan")
                .content("{\"strategyRunId\":\"" + strategyRunId + "\",\"includeAiTactics\":true}"));
    }

    private record PlannedTrip(UUID tripId, UUID strategyRunId) {
    }
}
