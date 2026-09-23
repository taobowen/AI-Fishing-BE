package com.aifishing.lake.ingestion;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeFishSpeciesRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.ingestion.repo.RawDataObjectRepository;
import com.aifishing.lake.ingestion.repo.WetlandRepository;
import com.aifishing.lake.ingestion.source.OntarioFeatureClient;
import com.aifishing.lake.ingestion.job.ImportJobRunner;
import com.aifishing.lake.ops.LakeOpsDedupe;
import com.aifishing.lake.ops.LakeOpsJob;
import com.aifishing.lake.ops.LakeOpsJobExecutor;
import com.aifishing.lake.ops.LakeOpsJobKind;
import com.aifishing.lake.ops.LakeOpsJobRepository;
import com.aifishing.lake.ops.LakeOpsJobStatus;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LakeIngestionIT extends AbstractIntegrationTest {

    @MockitoBean
    OntarioFeatureClient ontarioFeatureClient;

    @MockitoSpyBean
    ImportJobRunner importJobRunner;

    @Autowired
    LakeOpsJobExecutor lakeOpsJobExecutor;

    @Autowired
    LakeOpsJobRepository lakeOpsJobRepository;

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Autowired
    BathymetryPointRepository bathymetryPointRepository;

    @Autowired
    LakeWaterwayRepository waterwayRepository;

    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Autowired
    LakeFishSpeciesRepository fishSpeciesRepository;

    @Autowired
    WetlandRepository wetlandRepository;

    @Autowired
    LakeAccessPointRepository accessPointRepository;

    @Autowired
    RawDataObjectRepository rawDataObjectRepository;

    private final AtomicBoolean failBathymetryPoints = new AtomicBoolean(false);
    private final AtomicBoolean failShoreline = new AtomicBoolean(false);
    private final AtomicBoolean failAccessPoints = new AtomicBoolean(false);

    @BeforeEach
    void stubOntario() {
        failBathymetryPoints.set(false);
        failShoreline.set(false);
        failAccessPoints.set(false);
        when(ontarioFeatureClient.query(any(FeatureQuery.class))).thenAnswer(invocation -> {
            FeatureQuery query = invocation.getArgument(0);
            String layer = OntarioFixtures.layerKey(query.layerUrl());
            if (failShoreline.get() && "O1-14".equals(layer)) {
                return OntarioFixtures.invalidGeometryPage(query);
            }
            if (failBathymetryPoints.get() && "O1-27".equals(layer)) {
                return OntarioFixtures.invalidGeometryPage(query);
            }
            if (failAccessPoints.get() && "O7-15".equals(layer)) {
                return OntarioFixtures.invalidGeometryPage(query);
            }
            return OntarioFixtures.pagesFor(query);
        });
    }

    @Test
    void importPersistsPaginatedRawCanonicalAndAdminSummary() throws Exception {
        JsonNode result = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));
        assertThat(result.path("identityResolved").asBoolean()).isTrue();
        assertThat(result.path("ogfId").asInt()).isEqualTo(1001);
        assertThat(result.path("datasets")).hasSize(16);

        assertThat(boundaryRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(1);
        assertThat(waterwayRepository.countByLakeIdAndProviderAndType(DevSeedIds.LAKE_ID, "LIO", "SHORELINE")).isEqualTo(2);
        assertThat(rawDataObjectRepository.countByLakeIdAndDatasetType(DevSeedIds.LAKE_ID, DatasetType.SHORELINE.name()))
                .isEqualTo(2);
        assertThat(fishSpeciesRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(3);
        assertThat(fishSpeciesRepository.findByLakeId(DevSeedIds.LAKE_ID))
                .anyMatch(row -> row.getSourceSpeciesName().equals("Cisco") && row.getSpecies() == null)
                .anyMatch(row -> row.getSourceSpeciesName().equals("Walleye") && row.getSpecies() != null);

        var accessPoints = accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID);
        assertThat(accessPoints).hasSize(1);
        assertThat(accessPoints.getFirst().getName()).isEqualTo("Head Lake Launch");
        assertThat(accessPoints.getFirst().getType()).isEqualTo("BOAT_LAUNCH");
        assertThat(accessPoints.getFirst().getBoatLaunch()).isTrue();
        assertThat(accessPoints.getFirst().getShoreAccess()).isNull();
        assertThat(accessPoints.getFirst().getParking()).isTrue();
        assertThat(accessPoints.getFirst().getOwnershipType()).isEqualTo("MUNICIPAL");
        assertThat(accessPoints.getFirst().getSourceMetadata()).containsEntry("SITE_NAME", "Head Lake Launch");
        assertThat(accessPoints).noneMatch(row -> "Head Lake Far Launch".equals(row.getName()));
        var accessStatus = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.ACCESS_POINT, "LIO")
                .orElseThrow();
        assertThat(accessStatus.getRecordCount()).isEqualTo(1);
        assertThat(accessStatus.getMetadata()).containsEntry("rawFeatureCount", 2);
        assertThat(accessStatus.getMetadata()).containsEntry("associatedCount", 1);
        assertThat(accessStatus.getMetadata()).containsEntry("rejectedByDistanceCount", 1);

        List<String> geometryTypes = waterwayRepository.findByLakeId(DevSeedIds.LAKE_ID).stream()
                .map(row -> row.getGeometry().getGeometryType())
                .toList();
        assertThat(geometryTypes).contains("LineString", "Polygon");
        assertThat(waterwayRepository.findByLakeId(DevSeedIds.LAKE_ID))
                .extracting(row -> row.getGeometry().getSRID())
                .containsOnly(4326);
        assertThat(waterwayRepository.findByLakeId(DevSeedIds.LAKE_ID).stream().map(row -> (Geometry) row.getGeometry()))
                .allMatch(Geometry::isValid);

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/datasets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.datasetType=='WETLAND')].status", hasItem("AVAILABLE")))
                .andExpect(jsonPath("$[?(@.datasetType=='WETLAND')].recordCount", hasItem(0)))
                .andExpect(jsonPath("$[?(@.datasetType=='VEGETATION')].status", hasItem("NOT_AVAILABLE")))
                .andExpect(jsonPath("$[?(@.datasetType=='BOTTOM_SUBSTRATE')].status", hasItem("NOT_AVAILABLE")))
                .andExpect(jsonPath("$[?(@.datasetType=='REGULATION')].status", hasItem("PARTIAL")))
                .andExpect(jsonPath("$[?(@.datasetType=='BATHYMETRY_INDEX')].status", hasItem("AVAILABLE")))
                .andExpect(jsonPath("$[?(@.datasetType=='SHORELINE')].pageCount", hasItem(2)))
                .andExpect(jsonPath("$[?(@.datasetType=='SHORELINE')].rawRecordCount", hasItem(2)))
                .andExpect(jsonPath("$[?(@.datasetType=='SHORELINE')].transferLimitObserved", hasItem(true)))
                .andExpect(jsonPath("$[?(@.datasetType=='SHORELINE')].paginationComplete", hasItem(true)))
                .andExpect(jsonPath("$[?(@.datasetType=='SHORELINE')].paginationWarning").isEmpty());

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/data-summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityResolved", is(true)))
                .andExpect(jsonPath("$.notAvailableDatasets").value(notNullValue()));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Head Lake")))
                .andExpect(jsonPath("$.datasets").doesNotExist());
    }

    @Test
    void repeatImportDoesNotDuplicateCanonicalRows() throws Exception {
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));

        assertThat(boundaryRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(1);
        assertThat(waterwayRepository.countByLakeIdAndProviderAndType(DevSeedIds.LAKE_ID, "LIO", "SHORELINE")).isEqualTo(2);
        assertThat(bathymetryPointRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(1);
    }

    @Test
    void failedRefreshKeepsLastSuccessfulCanonical() throws Exception {
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));
        var first = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.BATHYMETRY_POINT, "LIO")
                .orElseThrow();
        Instant successful = first.getLastSuccessfulImportAt();
        Instant attempted = first.getLastAttemptedAt();
        assertThat(bathymetryPointRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(1);

        Thread.sleep(25);
        failBathymetryPoints.set(true);
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/datasets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.datasetType=='BATHYMETRY_POINT')].status", hasItem("FAILED")));

        var second = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.BATHYMETRY_POINT, "LIO")
                .orElseThrow();
        assertThat(second.getLastSuccessfulImportAt()).isEqualTo(successful);
        assertThat(second.getLastAttemptedAt()).isAfter(attempted);
        assertThat(second.getRecordCount()).isEqualTo(1);
        assertThat(bathymetryPointRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(1);

        var shoreline = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.SHORELINE, "LIO")
                .orElseThrow();
        assertThat(shoreline.getStatus()).isEqualTo(DatasetStatusCode.AVAILABLE);
    }

    @Test
    void oneDatasetFailedDoesNotBlockOthers() throws Exception {
        failShoreline.set(true);
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));

        assertThat(statusOf(DatasetType.SHORELINE)).isEqualTo(DatasetStatusCode.FAILED);
        assertThat(statusOf(DatasetType.WETLAND)).isEqualTo(DatasetStatusCode.AVAILABLE);
        assertThat(wetlandRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isZero();
        assertThat(statusOf(DatasetType.ACCESS_POINT)).isEqualTo(DatasetStatusCode.AVAILABLE);
        assertThat(statusOf(DatasetType.VEGETATION)).isEqualTo(DatasetStatusCode.NOT_AVAILABLE);
    }

    @Test
    void accessPointOnlyImportReplacesCanonicalAndLeavesOtherDatasets() throws Exception {
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));
        String shorelineVersion = waterwayRepository.findByLakeId(DevSeedIds.LAKE_ID).getFirst().getImportVersion();
        long fishCount = fishSpeciesRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO");
        accessPointRepository.save(PlanningFixtures.accessPoint(
                DevSeedIds.LAKE_ID, "Stale far ramp", 45.2, -79.5, true, false));
        assertThat(accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID))
                .anyMatch(row -> "Stale far ramp".equals(row.getName()));

        JsonNode accessImport = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")
                        .param("dataset", "ACCESS_POINT")));
        assertThat(accessImport.path("identityResolved").asBoolean()).isTrue();

        var accessPoints = accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID);
        assertThat(accessPoints).hasSize(1);
        assertThat(accessPoints.getFirst().getName()).isEqualTo("Head Lake Launch");
        assertThat(accessPoints).noneMatch(row -> "Stale far ramp".equals(row.getName()));
        assertThat(accessPoints).noneMatch(row -> "Head Lake Far Launch".equals(row.getName()));
        assertThat(waterwayRepository.findByLakeId(DevSeedIds.LAKE_ID).getFirst().getImportVersion())
                .isEqualTo(shorelineVersion);
        assertThat(fishSpeciesRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isEqualTo(fishCount);
        assertThat(statusOf(DatasetType.ACCESS_POINT)).isEqualTo(DatasetStatusCode.AVAILABLE);
    }

    @Test
    void failedAccessPointRefreshKeepsLastGoodCanonical() throws Exception {
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")));
        var first = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.ACCESS_POINT, "LIO")
                .orElseThrow();
        Instant successful = first.getLastSuccessfulImportAt();
        UUID keptId = accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID).getFirst().getId();

        Thread.sleep(25);
        failAccessPoints.set(true);
        awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")
                        .param("dataset", "ACCESS_POINT")));

        assertThat(statusOf(DatasetType.ACCESS_POINT)).isEqualTo(DatasetStatusCode.FAILED);
        var second = datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.LAKE_ID, DatasetType.ACCESS_POINT, "LIO")
                .orElseThrow();
        assertThat(second.getLastSuccessfulImportAt()).isEqualTo(successful);
        assertThat(accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID)).hasSize(1);
        assertThat(accessPointRepository.findByLakeId(DevSeedIds.LAKE_ID).getFirst().getId()).isEqualTo(keptId);
        assertThat(statusOf(DatasetType.SHORELINE)).isEqualTo(DatasetStatusCode.AVAILABLE);
    }

    @Test
    void fourLakesShareGenericPipelineWithDifferentCoverage() throws Exception {
        seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.17);
        seedLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90);
        seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);

        for (UUID lakeId : List.of(
                DevSeedIds.LAKE_ID, DevSeedIds.RICE_LAKE_ID, DevSeedIds.SCUGOG_LAKE_ID, DevSeedIds.SIMCOE_LAKE_ID
        )) {
            JsonNode imported = awaitLakeOpsJobResult(asDev(post("/api/v1/admin/lakes/" + lakeId + "/import")));
            assertThat(imported.path("identityResolved").asBoolean()).isTrue();
        }

        assertThat(statusOf(DevSeedIds.LAKE_ID, DatasetType.BATHYMETRY_INDEX)).isEqualTo(DatasetStatusCode.AVAILABLE);
        assertThat(statusOf(DevSeedIds.RICE_LAKE_ID, DatasetType.BATHYMETRY_INDEX)).isEqualTo(DatasetStatusCode.NOT_AVAILABLE);
        assertThat(statusOf(DevSeedIds.RICE_LAKE_ID, DatasetType.BATHYMETRY_POINT)).isEqualTo(DatasetStatusCode.NOT_AVAILABLE);
        assertThat(statusOf(DevSeedIds.SCUGOG_LAKE_ID, DatasetType.FISH_STOCKING)).isEqualTo(DatasetStatusCode.AVAILABLE);
        assertThat(datasetStatusRepository
                .findByLakeIdAndDatasetTypeAndProvider(DevSeedIds.SCUGOG_LAKE_ID, DatasetType.FISH_STOCKING, "LIO")
                .orElseThrow()
                .getRecordCount()).isZero();
        assertThat(statusOf(DevSeedIds.SIMCOE_LAKE_ID, DatasetType.WETLAND)).isEqualTo(DatasetStatusCode.AVAILABLE);
        assertThat(wetlandRepository.countByLakeIdAndProvider(DevSeedIds.SIMCOE_LAKE_ID, "LIO")).isEqualTo(1);
        assertThat(wetlandRepository.countByLakeIdAndProvider(DevSeedIds.LAKE_ID, "LIO")).isZero();

        String ids = DevSeedIds.LAKE_ID + "," + DevSeedIds.RICE_LAKE_ID + ","
                + DevSeedIds.SCUGOG_LAKE_ID + "," + DevSeedIds.SIMCOE_LAKE_ID;
        mockMvc.perform(asDev(get("/api/v1/admin/lakes/data-summary").param("ids", ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].identityResolved", is(true)));
    }

    @Test
    void equivalentInFlightImportReturnsSameJobId() throws Exception {
        LakeOpsJob queued = new LakeOpsJob();
        queued.setLakeId(DevSeedIds.LAKE_ID);
        queued.setKind(LakeOpsJobKind.IMPORT);
        queued.setDedupeKey(LakeOpsDedupe.importKey(null));
        queued.setStatus(LakeOpsJobStatus.QUEUED);
        queued = lakeOpsJobRepository.saveAndFlush(queued);

        String body = mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/import")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("jobId").asText()).isEqualTo(queued.getId().toString());
        assertThat(response.path("status").asText()).isEqualTo("QUEUED");
        verify(importJobRunner, times(0)).run(DevSeedIds.LAKE_ID);
    }

    @Test
    void duplicateClaimDoesNotRunTheRunner() {
        doReturn(new com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse(
                DevSeedIds.LAKE_ID,
                "Head Lake",
                true,
                null,
                1001L,
                "Head Lake",
                List.of()
        )).when(importJobRunner).run(any(UUID.class));

        LakeOpsJob job = new LakeOpsJob();
        job.setLakeId(DevSeedIds.LAKE_ID);
        job.setKind(LakeOpsJobKind.IMPORT);
        job.setDedupeKey(LakeOpsDedupe.importKey(null));
        job.setStatus(LakeOpsJobStatus.QUEUED);
        job = lakeOpsJobRepository.saveAndFlush(job);

        assertThat(lakeOpsJobExecutor.claimAndRun(job.getId())).isZero();
        assertThat(lakeOpsJobExecutor.claimAndRun(job.getId())).isZero();
        verify(importJobRunner, times(1)).run(DevSeedIds.LAKE_ID);
        assertThat(lakeOpsJobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(LakeOpsJobStatus.SUCCEEDED);
    }

    private DatasetStatusCode statusOf(DatasetType type) {
        return statusOf(DevSeedIds.LAKE_ID, type);
    }

    private DatasetStatusCode statusOf(UUID lakeId, DatasetType type) {
        return datasetStatusRepository.findByLakeIdAndDatasetTypeAndProvider(lakeId, type, "LIO")
                .orElseThrow()
                .getStatus();
    }
}
