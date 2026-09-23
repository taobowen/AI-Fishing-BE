package com.aifishing.strategy;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructureSourceFingerprint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.ai.FishingStrategyReasoner;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherProvider;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyIT extends AbstractIntegrationTest {

    @MockitoBean
    FishingStrategyReasoner reasoner;

    @MockitoBean
    WeatherProvider weatherProvider;

    @Autowired
    LakeAnalysisRunRepository analysisRunRepository;

    @Autowired
    LakeFeatureRepository featureRepository;

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Autowired
    FishingRestrictionRepository restrictionRepository;

    @Autowired
    AnalysisContextFactory contextFactory;

    @Autowired
    StructureSourceFingerprint fingerprint;

    @BeforeEach
    void stubWeatherAndReasoner() {
        when(weatherProvider.forecast(anyDouble(), anyDouble(), any(), any(), any(), any(), any()))
                .thenReturn(new WeatherContext(
                        WeatherAvailability.FORECAST_AVAILABLE,
                        Instant.parse("2026-09-02T16:00:00Z"),
                        "open-meteo",
                        "America/Toronto",
                        LocalDate.of(2026, 9, 12),
                        false,
                        null,
                        14.0,
                        10.0,
                        240.0,
                        0.1,
                        70.0,
                        1013.0,
                        LocalTime.of(6, 42),
                        LocalTime.of(19, 31),
                        List.of(),
                        "Air temperature is a forecast of air, not observed lake water temperature."
                ));
        when(reasoner.reason(any(), any())).thenReturn(
                new FishingStrategyReasoner.StrategyReasonerResult(StrategyFixtures.validProfile(), null, null));
    }

    @Test
    void headLakeStrategyPersistsCompletedProfileAndSecondarySpecies() throws Exception {
        ProcessingFixtures.seedStatus(DevSeedIds.LAKE_ID, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, 6, datasetStatusRepository);
        ProcessingFixtures.seedStatus(DevSeedIds.LAKE_ID, DatasetType.REGULATION, DatasetStatusCode.AVAILABLE, 1, datasetStatusRepository);
        ProcessingFixtures.seedRegulationText(DevSeedIds.LAKE_ID, restrictionRepository);
        seedAnalysis(DevSeedIds.LAKE_ID, AnalysisRunStatus.READY);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.HUMP, 0.82);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID);

        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/strategy/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.secondaryTargetSpecies[0]", is("WALLEYE")))
                .andExpect(jsonPath("$.weather.retrievedAt", is("2026-09-02T16:00:00Z")))
                .andExpect(jsonPath("$.weather.waterTemperatureAvailable", is(false)))
                .andExpect(jsonPath("$..rawText").doesNotExist())
                .andExpect(jsonPath("$.lake.regulations.structuredRestrictionCount", is(1)));

        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.strategyProfile.systemConfidence", not(nullValue())))
                .andExpect(jsonPath("$.strategyProfile.modelConfidence", is(0.88)))
                .andExpect(jsonPath("$.strategyProfile.timeWindows[0].structurePreferences[0].type", is("HUMP")));

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").doesNotExist())
                .andExpect(jsonPath("$.plans").doesNotExist());
    }

    @Test
    void strategyGenerateFetchesWeatherOnceForTheTrip() throws Exception {
        seedAnalysis(DevSeedIds.LAKE_ID, AnalysisRunStatus.READY);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.HUMP, 0.9);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.DROP_OFF, 0.8);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.FLAT, 0.7);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.BASIN, 0.75);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID);

        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
        verify(weatherProvider, times(1)).forecast(anyDouble(), anyDouble(), any(), any(), any(), any(), any());
    }

    @Test
    void failedRegenerationPreservesLastCompleted() throws Exception {
        seedAnalysis(DevSeedIds.LAKE_ID, AnalysisRunStatus.READY);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.HUMP, 0.8);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID);

        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        when(reasoner.reason(any(), any())).thenThrow(new IllegalStateException("model down"));
        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")));

        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.strategyProfile.modelConfidence", is(0.88)));

        mockMvc.perform(asDev(get("/api/v1/admin/trips/" + tripId + "/strategy-runs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void pipelineNotReadyFailsAndDoesNotCallReasoner() throws Exception {
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID);
        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", org.hamcrest.Matchers.containsString("NOT_READY")));
        verify(reasoner, never()).reason(any(), any());
    }

    @Test
    void readyAnalysisWithZeroFeaturesIsDegradedCompleted() throws Exception {
        seedAnalysis(DevSeedIds.LAKE_ID, AnalysisRunStatus.READY);
        UUID tripId = saveTrip(DevSeedIds.LAKE_ID);
        when(reasoner.reason(any(), any())).thenReturn(
                new FishingStrategyReasoner.StrategyReasonerResult(StrategyFixtures.zeroStructureProfile(), null, null));
        mockMvc.perform(asDev(post("/api/v1/admin/trips/" + tripId + "/strategy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.strategyProfile.dataLimitations[*].code", hasItem("STRUCTURE_NONE_AFTER_ANALYSIS")));
    }

    @Test
    void fourLakesProduceDifferentContextWithoutLakeNameBranches() throws Exception {
        ProcessingFixtures.seedStatus(DevSeedIds.LAKE_ID, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, 8, datasetStatusRepository);
        seedAnalysis(DevSeedIds.LAKE_ID, AnalysisRunStatus.READY);
        seedFeature(DevSeedIds.LAKE_ID, FeatureType.HUMP, 0.9);

        Lake rice = seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.16);
        ProcessingFixtures.seedStatus(rice.getId(), DatasetType.BATHYMETRY_LINE, DatasetStatusCode.NOT_AVAILABLE, 0, datasetStatusRepository);
        ProcessingFixtures.seedStatus(rice.getId(), DatasetType.SHORELINE, DatasetStatusCode.AVAILABLE, 1, datasetStatusRepository);
        seedAnalysis(rice.getId(), AnalysisRunStatus.READY);

        Lake scugog = seedLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90);
        ProcessingFixtures.seedStatus(scugog.getId(), DatasetType.BATHYMETRY_LINE, DatasetStatusCode.PARTIAL, 2, datasetStatusRepository);
        seedAnalysis(scugog.getId(), AnalysisRunStatus.PARTIAL);
        seedFeature(scugog.getId(), FeatureType.BASIN, 0.4);

        Lake simcoe = seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);
        ProcessingFixtures.seedStatus(simcoe.getId(), DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, 20, datasetStatusRepository);
        seedAnalysis(simcoe.getId(), AnalysisRunStatus.READY);
        seedFeature(simcoe.getId(), FeatureType.DROP_OFF, 0.7);
        seedFeature(simcoe.getId(), FeatureType.FLAT, 0.6);

        UUID headTrip = saveTrip(DevSeedIds.LAKE_ID);
        UUID riceTrip = saveTrip(rice.getId());
        UUID scugogTrip = saveTrip(scugog.getId());
        UUID simcoeTrip = saveTrip(simcoe.getId());

        String head = mockMvc.perform(asDev(get("/api/v1/admin/trips/" + headTrip + "/strategy/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lake.totalStructureCount", greaterThan(0)))
                .andExpect(jsonPath("$.lake.datasetCoverage.BATHYMETRY_LINE", is("AVAILABLE")))
                .andReturn().getResponse().getContentAsString();
        String riceJson = mockMvc.perform(asDev(get("/api/v1/admin/trips/" + riceTrip + "/strategy/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lake.totalStructureCount", is(0)))
                .andExpect(jsonPath("$.lake.datasetCoverage.BATHYMETRY_LINE", is("NOT_AVAILABLE")))
                .andReturn().getResponse().getContentAsString();
        String scugogJson = mockMvc.perform(asDev(get("/api/v1/admin/trips/" + scugogTrip + "/strategy/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lake.pipelineReadiness", is("PARTIAL")))
                .andReturn().getResponse().getContentAsString();
        String simcoeJson = mockMvc.perform(asDev(get("/api/v1/admin/trips/" + simcoeTrip + "/strategy/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lake.totalStructureCount", is(2)))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(head).isNotEqualTo(riceJson);
        org.assertj.core.api.Assertions.assertThat(scugogJson).isNotEqualTo(simcoeJson);
        org.assertj.core.api.Assertions.assertThat(head).contains("HUMP");
        org.assertj.core.api.Assertions.assertThat(simcoeJson).contains("DROP_OFF");
    }

    private void seedAnalysis(UUID lakeId, AnalysisRunStatus status) {
        Lake lake = lakeRepository.findById(lakeId).orElseThrow();
        String sourceSnapshotId = fingerprint.id(
                contextFactory.create(lake, "fingerprint", null, Pipeline.GIS).sourceDatasetSnapshot());
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(lakeId);
        run.setPipeline(Pipeline.GIS);
        run.setStatus(status);
        run.setAnalysisVersion("strategy-fixture");
        run.setAlgorithmVersion("1.0.0");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        run.setSourceSnapshotId(sourceSnapshotId);
        analysisRunRepository.save(run);
    }

    private void seedFeature(UUID lakeId, FeatureType type, double confidence) {
        LakeFeature feature = new LakeFeature();
        feature.setLakeId(lakeId);
        feature.setType(type);
        feature.setPipeline(Pipeline.GIS);
        feature.setGeometry(geoMapper.toPoint(new GeoPointDto(44.75, -78.92)));
        feature.setConfidence(BigDecimal.valueOf(confidence));
        feature.setSourceMethod("GIS");
        feature.setProvider("TEST");
        feature.setAnalysisVersion("strategy-fixture");
        featureRepository.save(feature);
    }

    private UUID saveTrip(UUID lakeId) {
        Trip trip = new Trip();
        trip.setUserId(DevSeedIds.USER_ID);
        trip.setLakeId(lakeId);
        trip.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        trip.setSecondaryTargetSpecies(List.of(FishSpecies.WALLEYE));
        trip.setPlannedDate(LocalDate.of(2026, 9, 12));
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        trip.setFishingMode(FishingMode.SHORE);
        trip.setStatus(TripStatus.DRAFT);
        return tripRepository.save(trip).getId();
    }
}
