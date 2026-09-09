package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripPlanningIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    LakeAccessPointRepository accessPointRepository;
    @Autowired
    LakeWaterwayRepository waterwayRepository;
    @Autowired
    FishingRestrictionRepository restrictionRepository;

    @Test
    void candidateQueryFiltersPipelineVersionTypeAndDepth() {
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.9);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.002, 40),
                18,
                22,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG + 0.002, PlanningFixtures.HEAD_LAT, 40),
                2.5,
                3.5,
                0.9,
                "other-version"
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.BASIN,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT - 0.002, 40),
                2.5,
                3.5,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        var matches = featureRepository.findCandidates(
                DevSeedIds.LAKE_ID,
                com.aifishing.lake.processing.dto.Pipeline.GIS,
                PlanningFixtures.ANALYSIS_VERSION,
                List.of(FeatureType.HUMP),
                java.math.BigDecimal.valueOf(2),
                java.math.BigDecimal.valueOf(4)
        );
        org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
    }

    @Test
    void candidateQueryTreatsSignedOntarioDepthAsWaterMeters() {
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40),
                -3.7,
                -2.7,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        var matches = featureRepository.findCandidates(
                DevSeedIds.LAKE_ID,
                com.aifishing.lake.processing.dto.Pipeline.GIS,
                PlanningFixtures.ANALYSIS_VERSION,
                List.of(FeatureType.HUMP),
                java.math.BigDecimal.valueOf(2),
                java.math.BigDecimal.valueOf(4)
        );
        org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
    }

    @Test
    void headLakePlanPersistsWaypointsBoundToRecordedSnapshot() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.86);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT + 0.002, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.8);
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Head launch", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID boatId = saveBoat(DevSeedIds.USER_ID, BoatType.FISHING_BOAT);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.BOAT, boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.strategyRunId", is(strategy.getId().toString())))
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")))
                .andExpect(jsonPath("$.plan.featureAnalysisVersion", is(PlanningFixtures.ANALYSIS_VERSION)))
                .andExpect(jsonPath("$.plan.waypoints.length()", greaterThan(0)))
                .andExpect(jsonPath("$.plan.waypoints[0].lakeFeatureId", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].location.lat", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].location.lng", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].scoreBreakdown.historicalPerformance", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].scoreBreakdown.historicalEvidenceConfidence", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].recommendedTechniques[0]", is("NED_RIG")))
                .andExpect(jsonPath("$.plan.plannedLaunchDepartureAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.plannedReturnAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.scheduleAlgorithmVersion", is("1.4.0")))
                .andExpect(jsonPath("$.plan.waypoints[0].plannedArrivalAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].plannedDwellMinutes", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].plannedVisitMinutes", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].targetKind", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].scoreBreakdown.intrinsic", not(nullValue())));

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("GENERATED")));

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan/map-data")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waypoints[0].featureId", not(nullValue())))
                .andExpect(jsonPath("$.boundary").doesNotExist())
                .andExpect(jsonPath("$.features").doesNotExist());

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans").doesNotExist())
                .andExpect(jsonPath("$.plan").doesNotExist());

        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/planning-runs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("COMPLETED")));
    }

    @Test
    void unknownStrategyRunIsNotFound() throws Exception {
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        mockMvc.perform(generateWith(tripId, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlanWithoutGeneratingIsNotFound() throws Exception {
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleSnapshotFailsAndPreservesPriorPlan() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.9);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.version", is(1)));

        featureRepository.findAll().forEach(feature -> {
            feature.setAnalysisVersion("retargeted");
            featureRepository.save(feature);
        });

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", org.hamcrest.Matchers.containsString("STALE_OR_MISSING_FEATURE_SNAPSHOT")))
                .andExpect(jsonPath("$.plan").doesNotExist());

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.status", is("GENERATED")));

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void unknownAccessNeverUsesLakeCentroidAndFailsWithoutKnownLaunches() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.84);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID boatId = saveBoat(DevSeedIds.USER_ID, BoatType.FISHING_BOAT);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.BOAT, boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("NO_KNOWN_BOAT_LAUNCH")))
                .andExpect(jsonPath("$.plan").doesNotExist());
    }

    @Test
    void shoreWithoutAccessPointsIsUnverifiedNotRejected() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        var shoreline = new com.aifishing.lake.ingestion.domain.LakeWaterway();
        shoreline.setLakeId(DevSeedIds.LAKE_ID);
        shoreline.setProvider(ProcessingFixtures.PROVIDER);
        shoreline.setSource("TEST");
        shoreline.setImportVersion(ProcessingFixtures.IMPORT_VERSION);
        shoreline.setSourceRecordId("shore-plan");
        shoreline.setType("SHORELINE");
        shoreline.setGeometry(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 520));
        waterwayRepository.save(shoreline);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG - 0.003, PlanningFixtures.ANALYSIS_VERSION, 0.77);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.warnings", hasItem("SHORE_ACCESS_UNVERIFIED")));
    }

    @Test
    void rawTextRestrictionDoesNotReject() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        ProcessingFixtures.seedRegulationText(DevSeedIds.LAKE_ID, restrictionRepository);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.8);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void fourLakesProduceDifferentPlansFromData() throws Exception {
        seedLakePlan(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 3);
        Lake rice = seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.16);
        seedLakePlan(rice.getId(), 44.18, -78.16, 1);
        Lake scugog = seedLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90);
        seedLakePlan(scugog.getId(), 44.15, -78.90, 2);
        Lake simcoe = seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);
        seedLakePlan(simcoe.getId(), 44.42, -79.37, 4);

        PlannedTrip headTrip = savePlannedTrip(DevSeedIds.LAKE_ID);
        PlannedTrip riceTrip = savePlannedTrip(rice.getId());
        PlannedTrip scugogTrip = savePlannedTrip(scugog.getId());
        PlannedTrip simcoeTrip = savePlannedTrip(simcoe.getId());

        String head = mockMvc.perform(generateWith(headTrip.tripId(), headTrip.strategyRunId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn().getResponse().getContentAsString();
        String riceJson = mockMvc.perform(generateWith(riceTrip.tripId(), riceTrip.strategyRunId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn().getResponse().getContentAsString();
        String scugogJson = mockMvc.perform(generateWith(scugogTrip.tripId(), scugogTrip.strategyRunId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn().getResponse().getContentAsString();
        String simcoeJson = mockMvc.perform(generateWith(simcoeTrip.tripId(), simcoeTrip.strategyRunId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(head).isNotEqualTo(riceJson);
        org.assertj.core.api.Assertions.assertThat(scugogJson).isNotEqualTo(simcoeJson);
        org.assertj.core.api.Assertions.assertThat(head).doesNotContain("if (Head)");
    }

    @Test
    void inflatableOnLongSimcoeSpanRejectsDistantSpot() throws Exception {
        Lake simcoe = seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);
        ProcessingFixtures.seedBoundary(simcoe.getId(), 44.42, -79.37, 20_000, boundaryRepository);
        double southLaunchLat = 44.42 - (19_700 / 111_320.0);
        seedHump(simcoe.getId(), southLaunchLat + 0.002, -79.37, PlanningFixtures.ANALYSIS_VERSION, 0.9);
        seedHump(simcoe.getId(), 44.52, -79.37, PlanningFixtures.ANALYSIS_VERSION, 0.88);
        ensureSpatialSnapshot(simcoe.getId());
        accessPointRepository.save(PlanningFixtures.accessPoint(simcoe.getId(), "South", southLaunchLat, -79.37, true, false));
        UUID boatId = saveBoat(DevSeedIds.USER_ID, BoatType.INFLATABLE);
        UUID tripId = saveTrip(simcoe.getId(), FishingMode.BOAT, boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/planning-runs")))
                .andExpect(status().isOk());

        var runs = mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/planning-runs")))
                .andReturn().getResponse().getContentAsString();
        String runId = objectMapper.readTree(runs).get(0).get("id").asText();
        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/planning-runs/" + runId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterSummary.rejections.BOAT_TRAVEL_UNREASONABLE", greaterThan(0)));
    }

    @Test
    void landCrossingSetsDetourMetadata() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        var island = new com.aifishing.lake.ingestion.domain.LakeWaterway();
        island.setLakeId(DevSeedIds.LAKE_ID);
        island.setProvider(ProcessingFixtures.PROVIDER);
        island.setSource("TEST");
        island.setImportVersion(ProcessingFixtures.IMPORT_VERSION);
        island.setSourceRecordId("island-plan");
        island.setType("ISLAND");
        island.setGeometry(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 60));
        waterwayRepository.save(island);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG - 0.004, PlanningFixtures.ANALYSIS_VERSION, 0.9);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG + 0.004, PlanningFixtures.ANALYSIS_VERSION, 0.85);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "West", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG - 0.004, true, false));
        UUID boatId = saveBoat(DevSeedIds.USER_ID, BoatType.FISHING_BOAT);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.BOAT, boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));

        String body = mockMvc.perform(generateWith(tripId, strategy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("landCrossingDetected");
    }

    @Test
    void otherUserCannotReadPlan() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, PlanningFixtures.ANALYSIS_VERSION, 0.8);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID, FishingMode.SHORE, null);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(generateWith(tripId, strategy.getId())).andExpect(status().isOk());
        mockMvc.perform(asOther(get("/api/v1/trips/" + tripId + "/plan"))).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generateWith(
            UUID tripId,
            UUID strategyRunId
    ) {
        return asDev(post("/api/v1/trips/" + tripId + "/plan")
                .content("{\"strategyRunId\":\"" + strategyRunId + "\"}"));
    }

    private record PlannedTrip(UUID tripId, UUID strategyRunId) {
    }

    private void seedLakePlan(UUID lakeId, double lat, double lng, int humps) {
        ProcessingFixtures.seedBoundary(lakeId, lat, lng, 900, boundaryRepository);
        for (int i = 0; i < humps; i++) {
            seedHump(lakeId, lat + (i * 0.0018), lng, PlanningFixtures.ANALYSIS_VERSION, 0.7 + (i * 0.03));
        }
        ensureSpatialSnapshot(lakeId);
    }

    private PlannedTrip savePlannedTrip(UUID lakeId) {
        UUID tripId = saveTrip(lakeId, FishingMode.SHORE, null);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        return new PlannedTrip(tripId, strategy.getId());
    }

    private void seedHump(UUID lakeId, double lat, double lng, String version, double confidence) {
        featureRepository.save(PlanningFixtures.feature(
                lakeId,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(lng, lat, 50),
                2.5,
                3.5,
                confidence,
                version
        ));
    }

    private UUID saveTrip(UUID lakeId, FishingMode mode, UUID boatId) {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, lakeId, mode);
        trip.setBoatId(boatId);
        return tripRepository.save(trip).getId();
    }

    private UUID saveBoat(UUID userId, BoatType type) {
        Boat boat = new Boat();
        boat.setUserId(userId);
        boat.setName(type.name());
        boat.setType(type);
        if (type == BoatType.INFLATABLE) {
            boat.setPropulsionTypes(List.of(PropulsionType.ELECTRIC_TROLLING));
            boat.setPrimaryTransitPropulsionType(PropulsionType.ELECTRIC_TROLLING);
            boat.setMotors(List.of(new com.aifishing.boat.domain.BoatMotor(
                    PropulsionType.ELECTRIC_TROLLING, null, null, null, BigDecimal.valueOf(55))));
        } else {
            boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
            boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
            boat.setMotors(List.of(new com.aifishing.boat.domain.BoatMotor(
                    PropulsionType.GAS_OUTBOARD, null, null, BigDecimal.valueOf(15), null)));
        }
        boat.setMaxSpeedKmh(type == BoatType.INFLATABLE ? BigDecimal.valueOf(8) : BigDecimal.valueOf(25));
        boat.setActive(true);
        return boatRepository.save(boat).getId();
    }
}

@TestPropertySource(properties = "app.strategy.feature-pipeline=VISION")
class TripPlanningPipelineIsolationIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Test
    void recordedGisSnapshotStillPlansWhenLiveYamlIsVision() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        UUID tripId = tripRepository.save(trip).getId();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, StrategyFixtures.validProfile(), objectMapper));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")));
    }
}
