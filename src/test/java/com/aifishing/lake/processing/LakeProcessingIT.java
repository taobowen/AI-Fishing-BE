package com.aifishing.lake.processing;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.domain.DerivedAnalysisArtifact;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.DerivedAnalysisArtifactRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.repo.LakeFeatureStatusRepository;
import com.aifishing.seed.DevSeedIds;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LakeProcessingIT extends AbstractIntegrationTest {

    @Autowired
    BathymetryContourRepository contourRepository;

    @Autowired
    BathymetryPointRepository bathymetryPointRepository;

    @Autowired
    LakeWaterwayRepository waterwayRepository;

    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Autowired
    LakeFeatureRepository featureRepository;

    @Autowired
    LakeFeatureStatusRepository featureStatusRepository;

    @Autowired
    DerivedAnalysisArtifactRepository artifactRepository;

    @Autowired
    FishingRestrictionRepository restrictionRepository;

    @Autowired
    com.aifishing.lake.processing.extract.AnalysisContextFactory contextFactory;

    @Autowired
    com.aifishing.lake.processing.service.StructureSourceFingerprint fingerprint;

    @Test
    void processExtractsMixedGeometryWithoutWholeLakeRaster() throws Exception {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedRegulationText(DevSeedIds.LAKE_ID, restrictionRepository);

        JsonNode processed = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        assertThat(processed.path("processingStatus").asText()).isEqualTo("READY");
        assertThat(processed.path("contourCount").asInt()).isGreaterThan(0);
        assertThat(processed.path("bathymetryPointCount").asInt()).isGreaterThan(0);
        assertThat(processed.path("featureCountByType").path("HUMP").asInt()).isGreaterThan(0);
        assertThat(processed.path("featureCountByType").path("BASIN").asInt()).isGreaterThan(0);
        assertThat(processed.path("featureCountByType").path("DROP_OFF").asInt()).isGreaterThan(0);
        assertThat(processed.path("featureCountByType").path("FLAT").asInt()).isGreaterThan(0);
        assertThat(processed.path("confidenceDistribution").isMissingNode()).isFalse();
        assertThat(processed.path("warnings").isMissingNode()).isFalse();

        List<LakeFeature> features = featureRepository.findByLakeId(DevSeedIds.LAKE_ID);
        assertThat(features).extracting(LakeFeature::getType)
                .contains(FeatureType.HUMP, FeatureType.BASIN, FeatureType.DROP_OFF);
        assertThat(features.stream().map(LakeFeature::getGeometry).map(Geometry::getGeometryType).toList())
                .contains("Polygon", "LineString");
        assertThat(features).allMatch(feature -> feature.getGeometry().getSRID() == 4326);
        assertThat(features).allMatch(feature -> "DERIVED".equals(feature.getProvider()));
        assertThat(features).noneMatch(feature -> "HOLE".equals(feature.getType().name()));

        List<FishingRestriction> restrictions = restrictionRepository.findByLakeId(DevSeedIds.LAKE_ID);
        assertThat(restrictions).hasSize(1);
        assertThat(restrictions.get(0).getGeometry()).isNull();
        assertThat(features).noneMatch(feature ->
                feature.getDerivationMetadata() != null
                        && String.valueOf(feature.getDerivationMetadata()).contains("SANCTUARY"));

        List<DerivedAnalysisArtifact> artifacts = artifactRepository.findByLakeIdAndAnalysisVersion(
                DevSeedIds.LAKE_ID,
                lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow().getCurrentAnalysisVersion()
        );
        assertThat(artifacts).isNotEmpty();
        assertThat(artifacts.get(0).getGridMetadata().get("wholeLakeRaster")).isEqualTo(false);
        assertThat(artifacts.get(0).getStorageUri()).isNull();
        assertThat(artifacts.get(0).getChecksumSha256()).hasSize(64);

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/features.geojson")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features.length()", greaterThan(0)));

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/features.geojson")
                        .param("type", "DROP_OFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[*].properties.type", hasItem("DROP_OFF")))
                .andExpect(jsonPath("$.features[*].properties.type", not(hasItem("HUMP"))));

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/features.geojson")
                        .param("minConfidence", "0.99")))
                .andExpect(status().isOk());

        String analysisVersion = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow().getCurrentAnalysisVersion();
        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/features.geojson")
                        .param("analysisVersion", analysisVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", greaterThan(0)));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").doesNotExist())
                .andExpect(jsonPath("$.features").doesNotExist());
    }

    @Test
    void repeatProcessDoesNotDuplicateRows() throws Exception {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        long first = featureRepository.findByLakeId(DevSeedIds.LAKE_ID).size();
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        assertThat(featureRepository.findByLakeId(DevSeedIds.LAKE_ID)).hasSize((int) first);
    }

    @Test
    void availableZeroVersusNotAvailable() throws Exception {
        ProcessingFixtures.seedStraightShoreline(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                waterwayRepository, datasetStatusRepository
        );
        JsonNode job = awaitLakeOpsJob(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        assertThat(job.path("status").asText()).isEqualTo("FAILED");
        assertThat(job.path("failureCode").asText()).isEqualTo("NO_PERSISTED_FEATURES");
        assertThat(job.path("result").path("processingStatus").asText()).isEqualTo("READY");

        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.POINT)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.AVAILABLE);
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.POINT)
                .orElseThrow().getRecordCount()).isZero();
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.HUMP)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.NOT_AVAILABLE);
        assertThat(featureRepository.countByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.HUMP)).isZero();
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.ISLAND_EDGE)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.NOT_AVAILABLE);
    }

    @Test
    void fourLakesSharePipelineAndQualityReportFields() throws Exception {
        seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.17);
        seedLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90);
        seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);

        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedShorelineOnly(
                DevSeedIds.RICE_LAKE_ID, 44.18, -78.17,
                waterwayRepository, boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedBathyOnly(
                DevSeedIds.SCUGOG_LAKE_ID, 44.15, -78.90,
                contourRepository, boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.SIMCOE_LAKE_ID, 44.42, -79.37,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );

        for (UUID lakeId : List.of(
                DevSeedIds.LAKE_ID,
                DevSeedIds.RICE_LAKE_ID,
                DevSeedIds.SCUGOG_LAKE_ID,
                DevSeedIds.SIMCOE_LAKE_ID
        )) {
            JsonNode processed = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + lakeId + "/process")));
            assertThat(processed.path("processingStatus").asText()).isEqualTo("READY");
        }

        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.RICE_LAKE_ID, FeatureType.HUMP)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.NOT_AVAILABLE);
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.SCUGOG_LAKE_ID, FeatureType.POINT)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.NOT_AVAILABLE);
        assertThat(featureRepository.countByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.HUMP)).isPositive();
        assertThat(featureRepository.countByLakeIdAndType(DevSeedIds.SCUGOG_LAKE_ID, FeatureType.HUMP)).isPositive();

        String ids = DevSeedIds.LAKE_ID + "," + DevSeedIds.RICE_LAKE_ID + ","
                + DevSeedIds.SCUGOG_LAKE_ID + "," + DevSeedIds.SIMCOE_LAKE_ID;
        mockMvc.perform(asDev(get("/api/v1/admin/lakes/data-summary").param("ids", ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].analysis.processingStatus", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.contourCount", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.bathymetryPointCount", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.processingDurationMs", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.featureCountByType", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.confidenceDistribution", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.notAvailableFeatureTypes", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.failedFeatureTypes", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.warnings", notNullValue()))
                .andExpect(jsonPath("$[0].analysis.bathymetryAvailability.BATHYMETRY_LINE", notNullValue()));
    }

    @Test
    void sourceFingerprintMatchesFullContextWithoutLoadingAllGeometries() {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        var lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        String fromCreate = fingerprint.id(contextFactory.create(lake, "fingerprint", null).sourceDatasetSnapshot());
        String fromLite = fingerprint.id(contextFactory.sourceDatasetSnapshot(lake));
        assertThat(fromLite).isEqualTo(fromCreate).startsWith("src-");

        ProcessingFixtures.seedStatus(
                DevSeedIds.LAKE_ID, DatasetType.ACCESS_POINT, DatasetStatusCode.AVAILABLE, 105, datasetStatusRepository);
        String afterAccessPoint = fingerprint.id(contextFactory.sourceDatasetSnapshot(lake));
        assertThat(afterAccessPoint).isEqualTo(fromLite);

        java.util.Map<String, Object> legacyFull = new java.util.LinkedHashMap<>(
                contextFactory.sourceDatasetSnapshot(lake));
        legacyFull.put("ACCESS_POINT", java.util.Map.of("status", "AVAILABLE", "recordCount", 105));
        assertThat(fingerprint.id(contextFactory.structureSourceSubset(legacyFull))).isEqualTo(fromLite);
    }

    @Test
    void reprocessKeepsWaypointReferencedFeatures() throws Exception {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        JsonNode processed = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        assertThat(processed.path("processingStatus").asText()).isEqualTo("READY");
        LakeFeature referenced = featureRepository.findByLakeIdAndPipelineAndType(
                DevSeedIds.LAKE_ID, com.aifishing.lake.processing.dto.Pipeline.GIS, FeatureType.HUMP).get(0);
        UUID tripId = tripRepository.save(com.aifishing.planning.PlanningFixtures.trip(
                DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, com.aifishing.common.enums.FishingMode.SHORE)).getId();
        com.aifishing.planning.domain.TripPlan plan = new com.aifishing.planning.domain.TripPlan();
        plan.setTripId(tripId);
        plan.setVersion(1);
        plan.setStatus(com.aifishing.common.enums.TripPlanStatus.GENERATED);
        plan.setGeneratedAt(java.time.Instant.now());
        plan = tripPlanRepository.save(plan);
        com.aifishing.planning.domain.TripWaypoint waypoint = new com.aifishing.planning.domain.TripWaypoint();
        waypoint.setTripPlanId(plan.getId());
        waypoint.setSequence(1);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(44.75, -78.92)));
        waypoint.setLakeFeatureId(referenced.getId());
        tripWaypointRepository.save(waypoint);

        JsonNode reprocessed = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")));
        assertThat(reprocessed.path("processingStatus").asText()).isEqualTo("READY");
        assertThat(reprocessed.path("failedFeatureTypes")).isEmpty();
        assertThat(featureRepository.findById(referenced.getId())).isPresent();
        assertThat(featureRepository.findByLakeIdAndPipelineAndType(
                DevSeedIds.LAKE_ID, com.aifishing.lake.processing.dto.Pipeline.GIS, FeatureType.HUMP).size())
                .isGreaterThan(1);
    }
}
