package com.aifishing.launch;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LaunchSelectionIT extends AbstractIntegrationTest {

    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    LakeAccessPointRepository accessPointRepository;
    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;

    @Test
    void boatTripDefaultsAutoAndShoreClearsSelection() throws Exception {
        UUID boatId = saveBoat();
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.launchSelection.mode", is("AUTO_RECOMMENDED")))
                .andReturn();
        String tripId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(patch("/api/v1/trips/" + tripId)).content("""
                        { "fishingMode": "SHORE", "boatId": null }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fishingMode", is("SHORE")))
                .andExpect(jsonPath("$.launchSelection").doesNotExist());
    }

    @Test
    void boatLaunchesAndCustomPreviewAreAuthenticatedAndMeterSnapped() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 400, boundaryRepository);
        LakeAccessPoint official = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Head launch", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, true, false));
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Shore only", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG + 0.001, false, true));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/boat-launches")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(official.getId().toString())))
                .andExpect(jsonPath("$[0].verification", is("AUTHORITATIVE")))
                .andExpect(jsonPath("$[0].routable", is(true)))
                .andExpect(jsonPath("$[0].ownershipType", is("MUNICIPAL")))
                .andExpect(jsonPath("$[0].location.lat", closeTo(PlanningFixtures.HEAD_LAT, 0.0001)));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + UUID.randomUUID() + "/boat-launches")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asDev(post("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/boat-launches/custom-preview"))
                        .content("""
                                { "requestedPoint": { "lat": %s, "lng": %s } }
                                """.formatted(PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shoreAccessPoint.lat", not(nullValue())))
                .andExpect(jsonPath("$.routeStartPoint.lat", not(nullValue())))
                .andExpect(jsonPath("$.nearbyOfficial[0].id", is(official.getId().toString())));

        mockMvc.perform(asDev(post("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/boat-launches/custom-preview"))
                        .content("""
                                { "requestedPoint": { "lat": 45.2, "lng": -79.5 } }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CUSTOM_LAUNCH_TOO_FAR_FROM_SHORE")));
    }

    @Test
    void officialAndCustomGenerateUseExactOriginsAndConflictOnOverride() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint a = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Alpha ramp", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false));
        LakeAccessPoint b = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Bravo ramp", PlanningFixtures.HEAD_LAT + 0.001, PlanningFixtures.HEAD_LNG, true, false));
        UUID boatId = saveBoat();

        MvcResult officialTrip = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": { "mode": "OFFICIAL_SELECTED", "officialAccessPointId": "%s" }
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId, a.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.launchSelection.mode", is("OFFICIAL_SELECTED")))
                .andExpect(jsonPath("$.launchSelection.officialAccessPointId", is(a.getId().toString())))
                .andReturn();
        UUID officialTripId = UUID.fromString(
                objectMapper.readTree(officialTrip.getResponse().getContentAsString()).get("id").asText());
        var officialStrategy = strategyRunRepository.save(
                PlanningFixtures.completedStrategy(officialTripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(asDev(post("/api/v1/trips/" + officialTripId + "/plan")
                        .content("{\"strategyRunId\":\"" + officialStrategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.mode", is("OFFICIAL_SELECTED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(a.getId().toString())));

        mockMvc.perform(asDev(post("/api/v1/trips/" + officialTripId + "/plan")
                        .content("""
                                {"strategyRunId":"%s","accessPointId":"%s"}
                                """.formatted(officialStrategy.getId(), b.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("LAUNCH_SELECTION_CONFLICT")));

        MvcResult customTrip = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": {
                            "mode": "CUSTOM_SELECTED",
                            "requestedPoint": { "lat": %s, "lng": %s }
                          }
                        }
                        """.formatted(
                        DevSeedIds.LAKE_ID, boatId, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.launchSelection.mode", is("CUSTOM_SELECTED")))
                .andExpect(jsonPath("$.launchSelection.routeStartPoint.lat", not(nullValue())))
                .andReturn();
        var customJson = objectMapper.readTree(customTrip.getResponse().getContentAsString());
        UUID customTripId = UUID.fromString(customJson.get("id").asText());
        double persistedLat = customJson.get("launchSelection").get("routeStartPoint").get("lat").asDouble();
        double persistedLng = customJson.get("launchSelection").get("routeStartPoint").get("lng").asDouble();
        var customStrategy = strategyRunRepository.save(
                PlanningFixtures.completedStrategy(customTripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(asDev(post("/api/v1/trips/" + customTripId + "/plan")
                        .content("{\"strategyRunId\":\"" + customStrategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.mode", is("CUSTOM_SELECTED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.routeStartPoint.lat", closeTo(persistedLat, 1e-8)))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.routeStartPoint.lng", closeTo(persistedLng, 1e-8)));

        mockMvc.perform(asDev(post("/api/v1/trips/" + customTripId + "/plan")
                        .content("""
                                {"strategyRunId":"%s","accessPointId":"%s"}
                                """.formatted(customStrategy.getId(), a.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("LAUNCH_SELECTION_CONFLICT")));
    }

    @Test
    void changingTripLaunchDoesNotRewriteExistingPlan() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint a = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Alpha ramp", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false));
        LakeAccessPoint b = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Bravo ramp", PlanningFixtures.HEAD_LAT + 0.001, PlanningFixtures.HEAD_LNG, true, false));
        UUID boatId = saveBoat();
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": { "mode": "OFFICIAL_SELECTED", "officialAccessPointId": "%s" }
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId, a.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tripId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.version", is(1)))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(a.getId().toString())));

        mockMvc.perform(asDev(patch("/api/v1/trips/" + tripId)).content("""
                        { "launchSelection": { "mode": "OFFICIAL_SELECTED", "officialAccessPointId": "%s" } }
                        """.formatted(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.launchSelection.officialAccessPointId", is(b.getId().toString())));

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.metadata.launchSelection.officialAccessPointId", is(a.getId().toString())));
    }

    @Test
    void noRoutableKnownLaunchIsDistinctFromNoKnownLaunch() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 80, boundaryRepository);
        seedHump();
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Too far ramp", 45.2, -79.5, true, false));
        UUID boatId = saveBoat();
        UUID tripId = saveTrip(boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("NO_ROUTABLE_KNOWN_BOAT_LAUNCH")));
    }

    @Test
    void customMovedResolutionRequiresReconfirm() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        UUID boatId = saveBoat();
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": {
                            "mode": "CUSTOM_SELECTED",
                            "requestedPoint": { "lat": %s, "lng": %s }
                          }
                        }
                        """.formatted(
                        DevSeedIds.LAKE_ID, boatId, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tripId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        jdbcTemplate.update("""
                UPDATE trip_launch_selections
                SET resolution_version = 'stale-v0',
                    route_start_point = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                WHERE trip_id = ?
                """, -79.4, 45.1, tripId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("CUSTOM_LAUNCH_MOVED")));
    }

    @Test
    void autoRunScopedAccessPointIdDoesNotPersistOnTrip() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint a = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Alpha ramp", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false));
        UUID boatId = saveBoat();
        UUID tripId = saveTrip(boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("""
                                {"strategyRunId":"%s","accessPointId":"%s"}
                                """.formatted(strategy.getId(), a.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(a.getId().toString())));
        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.launchSelection.mode", is("AUTO_RECOMMENDED")))
                .andExpect(jsonPath("$.launchSelection.officialAccessPointId").doesNotExist());
    }

    @Test
    void listIncludesPrivateWithPermissionWarning() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        LakeAccessPoint municipal = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Alpha municipal", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, true, false));
        LakeAccessPoint privateRamp = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Private ramp", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG + 0.0004, true, false);
        privateRamp.setOwnershipType(AccessOwnership.PRIVATE);
        accessPointRepository.save(privateRamp);
        LakeAccessPoint unknown = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Unknown ramp", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG - 0.0004, true, false);
        unknown.setOwnershipType(AccessOwnership.UNKNOWN);
        accessPointRepository.save(unknown);

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/boat-launches")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[?(@.id=='%s')].ownershipType".formatted(municipal.getId()), hasItem("MUNICIPAL")))
                .andExpect(jsonPath("$[?(@.id=='%s')].warnings[*]".formatted(privateRamp.getId()),
                        hasItem(AccessOwnership.PRIVATE_LAUNCH_PERMISSION_REQUIRED)))
                .andExpect(jsonPath("$[?(@.id=='%s')].warnings[*]".formatted(unknown.getId()),
                        hasItem(AccessOwnership.OWNERSHIP_UNVERIFIED)));
    }

    @Test
    void autoSkipsPrivateAndUsesUnknownWithUnverifiedWarning() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint privateRamp = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Aaa private", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false);
        privateRamp.setOwnershipType(AccessOwnership.PRIVATE);
        accessPointRepository.save(privateRamp);
        LakeAccessPoint unknown = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Zed unknown", PlanningFixtures.HEAD_LAT + 0.001, PlanningFixtures.HEAD_LNG, true, false);
        unknown.setOwnershipType(AccessOwnership.UNKNOWN);
        accessPointRepository.save(unknown);
        UUID boatId = saveBoat();
        UUID tripId = saveTrip(boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(unknown.getId().toString())))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.warnings", hasItem(AccessOwnership.OWNERSHIP_UNVERIFIED)))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.autoScore.accessConfidence", is(0.55)));
    }

    @Test
    void autoWithOnlyPrivateLaunchIsNoKnownBoatLaunch() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint privateRamp = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Private only", PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, true, false);
        privateRamp.setOwnershipType(AccessOwnership.PRIVATE);
        accessPointRepository.save(privateRamp);
        UUID boatId = saveBoat();
        UUID tripId = saveTrip(boatId);
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("NO_KNOWN_BOAT_LAUNCH")));
    }

    @Test
    void officialSelectedHonorsPublicAndPrivateWhenRoutable() throws Exception {
        ProcessingFixtures.seedBoundary(
                DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        seedHump();
        LakeAccessPoint municipal = accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Public ramp", PlanningFixtures.HEAD_LAT - 0.001, PlanningFixtures.HEAD_LNG, true, false));
        LakeAccessPoint privateRamp = PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Private pick", PlanningFixtures.HEAD_LAT + 0.001, PlanningFixtures.HEAD_LNG, true, false);
        privateRamp.setOwnershipType(AccessOwnership.PRIVATE);
        accessPointRepository.save(privateRamp);
        UUID boatId = saveBoat();

        MvcResult publicTrip = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": { "mode": "OFFICIAL_SELECTED", "officialAccessPointId": "%s" }
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId, municipal.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID publicTripId = UUID.fromString(objectMapper.readTree(publicTrip.getResponse().getContentAsString()).get("id").asText());
        var publicStrategy = strategyRunRepository.save(
                PlanningFixtures.completedStrategy(publicTripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + publicTripId + "/plan")
                        .content("{\"strategyRunId\":\"" + publicStrategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(municipal.getId().toString())));

        MvcResult privateTrip = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "launchSelection": { "mode": "OFFICIAL_SELECTED", "officialAccessPointId": "%s" }
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId, privateRamp.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID privateTripId = UUID.fromString(objectMapper.readTree(privateTrip.getResponse().getContentAsString()).get("id").asText());
        var privateStrategy = strategyRunRepository.save(
                PlanningFixtures.completedStrategy(privateTripId, PlanningFixtures.profile(), objectMapper));
        mockMvc.perform(asDev(post("/api/v1/trips/" + privateTripId + "/plan")
                        .content("{\"strategyRunId\":\"" + privateStrategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.officialAccessPointId", is(privateRamp.getId().toString())))
                .andExpect(jsonPath("$.plan.metadata.launchSelection.warnings",
                        hasItem(AccessOwnership.PRIVATE_LAUNCH_PERMISSION_REQUIRED)));
    }

    private void seedHump() {
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.86,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    private UUID saveTrip(UUID boatId) {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.BOAT);
        trip.setBoatId(boatId);
        return tripRepository.save(trip).getId();
    }

    private UUID saveBoat() {
        Boat boat = new Boat();
        boat.setUserId(DevSeedIds.USER_ID);
        boat.setName("Launch test");
        boat.setType(BoatType.FISHING_BOAT);
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setMotors(List.of(new com.aifishing.boat.domain.BoatMotor(
                PropulsionType.GAS_OUTBOARD, null, null, BigDecimal.valueOf(15), null)));
        boat.setActive(true);
        return boatRepository.save(boat).getId();
    }
}
