package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.capability.BoatCapabilityReasoner;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoatCapabilityPlanningIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    LakeAccessPointRepository accessPointRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;

    @MockitoBean
    BoatCapabilityReasoner reasoner;

    private UUID nearId;
    private UUID farId;

    @BeforeEach
    void seedLakeAndMockReasoner() {
        when(reasoner.estimate(any(), anyList())).thenThrow(new IllegalStateException("no live openai"));
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 15_000, boundaryRepository);
        double launchLat = PlanningFixtures.HEAD_LAT - (14_700 / 111_320.0);
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Head launch", launchLat, PlanningFixtures.HEAD_LNG, true, false));
        LakeFeature near = featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, launchLat + 0.002, 50),
                2.5,
                3.5,
                0.80,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        LakeFeature far = featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, launchLat + 0.090, 50),
                2.5,
                3.5,
                0.95,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        nearId = near.getId();
        farId = far.getId();
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    @Test
    void boatsAbcChangeFeasibilityNotFishingScores() throws Exception {
        JsonNode planA = generate(saveBoatA(), FishingMode.BOAT);
        JsonNode planB = generate(saveBoatB(), FishingMode.BOAT);
        JsonNode planC = generate(saveBoatC(), FishingMode.BOAT);

        List<UUID> aIds = featureIds(planA);
        List<UUID> bIds = featureIds(planB);
        List<UUID> cIds = featureIds(planC);

        assertThat(aIds).contains(nearId);
        assertThat(aIds).doesNotContain(farId);
        assertThat(planA.path("filterSummary").path("rejections").path("BOAT_TRAVEL_UNREASONABLE").asInt()).isGreaterThan(0);
        assertThat(planA.path("filterSummary").path("accepted").asInt())
                .isLessThan(planB.path("filterSummary").path("accepted").asInt());
        assertThat(planA.path("warnings").toString()).contains("BOAT_RANGE_LOW_CONFIDENCE");

        assertThat(bIds).contains(farId);
        assertThat(cIds).contains(farId);

        double minutesB = planB.path("plan").path("totalEstimatedTravelMinutes").asDouble();
        double minutesC = planC.path("plan").path("totalEstimatedTravelMinutes").asDouble();
        assertThat(minutesC).isLessThanOrEqualTo(minutesB);

        assertThat(score(planA, nearId)).isEqualTo(score(planB, nearId));
        assertThat(score(planB, farId)).isEqualTo(score(planC, farId));

        assertThat(planA.path("plan").path("planningAlgorithmVersion").asText()).isEqualTo("1.4.0");
        assertThat(cruise(planA)).isLessThan(cruise(planB));
        assertThat(cruise(planB)).isLessThanOrEqualTo(cruise(planC));
        JsonNode usableA = planA.path("plan").path("metadata").path("boatCapability").path("usableRange");
        assertThat(usableA.path("effectiveUsable").isMissingNode() || usableA.path("effectiveUsable").isNull()).isTrue();
        JsonNode usableB = planB.path("plan").path("metadata").path("boatCapability").path("usableRange");
        assertThat(usableB.path("estimatedPractical").asDouble()).isGreaterThan(0);
        assertThat(usableB.path("systemUsable").asDouble())
                .isEqualTo(usableB.path("estimatedPractical").asDouble() * 0.70, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void comfortableRangeCapsReturnToLaunchWithoutDoubleReserve() throws Exception {
        UUID boatId = saveBoat(PropulsionType.GAS_OUTBOARD, BigDecimal.valueOf(15), null, BigDecimal.valueOf(6));
        JsonNode plan = generate(boatId, FishingMode.BOAT);
        assertThat(featureIds(plan)).doesNotContain(farId);
        JsonNode usable = plan.path("plan").path("metadata").path("boatCapability").path("usableRange");
        assertThat(usable.path("comfortableCap").asDouble()).isEqualTo(6.0);
        assertThat(usable.path("effectiveUsable").asDouble()).isLessThanOrEqualTo(6.0);
    }

    @Test
    void shorePlanSkipsBoatResolver() throws Exception {
        JsonNode plan = generate(null, FishingMode.SHORE);
        assertThat(plan.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(plan.path("plan").path("metadata").path("boatCapability").isMissingNode()
                || plan.path("plan").path("metadata").path("boatCapability").isNull()).isTrue();
    }

    private JsonNode generate(UUID boatId, FishingMode mode) throws Exception {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, mode);
        trip.setBoatId(boatId);
        UUID tripId = tripRepository.save(trip).getId();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));
        String body = mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private UUID saveBoatA() {
        return saveBoat(PropulsionType.ELECTRIC_TROLLING, null, BigDecimal.valueOf(55), null);
    }

    private UUID saveBoatB() {
        return saveBoat(PropulsionType.GAS_OUTBOARD, BigDecimal.valueOf(9.9), null, null);
    }

    private UUID saveBoatC() {
        return saveBoat(PropulsionType.GAS_OUTBOARD, BigDecimal.valueOf(15), null, null);
    }

    private UUID saveBoat(
            PropulsionType transit,
            BigDecimal horsepower,
            BigDecimal thrustLb,
            BigDecimal comfortableRangeKm
    ) {
        Boat boat = new Boat();
        boat.setUserId(DevSeedIds.USER_ID);
        boat.setName(transit.name() + (horsepower == null ? "" : horsepower));
        boat.setType(BoatType.INFLATABLE);
        boat.setPropulsionTypes(List.of(transit));
        boat.setPrimaryTransitPropulsionType(transit);
        boat.setMotors(List.of(new BoatMotor(transit, null, null, horsepower, thrustLb)));
        boat.setComfortableRoundTripRangeKm(comfortableRangeKm);
        boat.setActive(true);
        return boatRepository.save(boat).getId();
    }

    private static List<UUID> featureIds(JsonNode generateResponse) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode waypoint : generateResponse.path("plan").path("waypoints")) {
            if (waypoint.hasNonNull("lakeFeatureId")) {
                ids.add(UUID.fromString(waypoint.get("lakeFeatureId").asText()));
            }
        }
        return ids;
    }

    private static double score(JsonNode generateResponse, UUID featureId) {
        for (JsonNode waypoint : generateResponse.path("plan").path("waypoints")) {
            if (featureId.toString().equals(waypoint.path("lakeFeatureId").asText())) {
                return waypoint.path("candidateScore").asDouble();
            }
        }
        throw new AssertionError("feature not in plan: " + featureId);
    }

    private static double cruise(JsonNode generateResponse) {
        return generateResponse.path("plan").path("metadata").path("boatCapability")
                .path("baseline").path("cruiseSpeedKmh").path("value").asDouble();
    }
}
