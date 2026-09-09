package com.aifishing.strategy.context;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.domain.LakeFishSpecies;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.FishHabitatRepository;
import com.aifishing.lake.ingestion.repo.FishStockingRecordRepository;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeFishSpeciesRepository;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.processing.admin.LakeAnalysisQuality;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import com.aifishing.strategy.StrategyProperties;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LakeContextBuilderTest {

    @Mock LakeDatasetStatusRepository datasetStatusRepository;
    @Mock LakeFishSpeciesRepository fishSpeciesRepository;
    @Mock FishStockingRecordRepository stockingRepository;
    @Mock FishHabitatRepository habitatRepository;
    @Mock FishingRestrictionRepository restrictionRepository;
    @Mock LakeFeatureRepository featureRepository;
    @Mock AnalysisQualityAssembler qualityAssembler;
    @Mock StructurePipelineReadinessService readinessService;

    private LakeContextBuilder builder;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        StrategyProperties properties = new StrategyProperties();
        properties.setFeaturePipeline(Pipeline.GIS);
        builder = new LakeContextBuilder(
                datasetStatusRepository,
                fishSpeciesRepository,
                stockingRepository,
                habitatRepository,
                restrictionRepository,
                featureRepository,
                qualityAssembler,
                properties,
                readinessService
        );
        org.mockito.Mockito.lenient().when(stockingRepository.findByLakeId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(habitatRepository.findByLakeId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(qualityAssembler.averageConfidence(anyList())).thenReturn(null);
        org.mockito.Mockito.lenient().when(qualityAssembler.quality(any(), any())).thenReturn(quality(null));
    }

    @Test
    void regulationCoverageOmitsRawText() throws Exception {
        UUID lakeId = UUID.randomUUID();
        FishingRestriction restriction = new FishingRestriction();
        restriction.setRawText("Sanctuary from the point at 44.75N 78.92W west along the shoreline.");
        restriction.setRestrictionType("SANCTUARY");
        when(restrictionRepository.findByLakeId(lakeId)).thenReturn(List.of(restriction));
        when(datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId)).thenReturn(List.of(
                status(lakeId, DatasetType.REGULATION, DatasetStatusCode.PARTIAL)
        ));
        when(fishSpeciesRepository.findByLakeId(lakeId)).thenReturn(List.of());
        stubReady(lakeId, 0);
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(eq(lakeId), eq(Pipeline.GIS), eq("test")))
                .thenReturn(List.of());

        LakeStrategyContext context = builder.build(lake(lakeId, "Head Lake"));
        String json = objectMapper.writeValueAsString(context);
        assertThat(json).doesNotContain("Sanctuary from the point");
        assertThat(json).doesNotContain("rawText");
        assertThat(context.regulations().coverageStatus()).isEqualTo("PARTIAL");
        assertThat(context.regulations().structuredRestrictionCount()).isEqualTo(1);
        assertThat(context.dataLimitations()).anyMatch(item -> item.code() == DataLimitationCode.REGULATION_COVERAGE_PARTIAL);
    }

    @Test
    void pipelineNotReadyWhenNoSuccessfulRun() {
        UUID lakeId = UUID.randomUUID();
        stubEmpty(lakeId);
        when(readinessService.evaluate(lakeId, Pipeline.GIS)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.GIS, StructurePipelineAvailability.NOT_PROCESSED, null, 0, null));
        LakeStrategyContext context = builder.build(lake(lakeId, "Head Lake"));
        assertThat(context.pipelineReadiness()).isEqualTo(PipelineReadiness.NOT_READY);
        assertThat(context.dataLimitations()).anyMatch(item -> item.code() == DataLimitationCode.STRUCTURE_PIPELINE_NOT_READY);
    }

    @Test
    void successfulAnalysisWithZeroFeaturesIsDegradedNotPipelineFailure() {
        UUID lakeId = UUID.randomUUID();
        stubEmpty(lakeId);
        stubEmptyReady(lakeId);
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(eq(lakeId), eq(Pipeline.GIS), eq("test")))
                .thenReturn(List.of());
        LakeStrategyContext context = builder.build(lake(lakeId, "Head Lake"));
        assertThat(context.pipelineReadiness()).isEqualTo(PipelineReadiness.READY);
        assertThat(context.analysisVersion()).isEqualTo("test");
        assertThat(context.totalStructureCount()).isZero();
        assertThat(context.dataLimitations()).anyMatch(item -> item.code() == DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS);
        assertThat(context.dataLimitations()).noneMatch(item -> item.code() == DataLimitationCode.STRUCTURE_PIPELINE_NOT_READY);
    }

    @Test
    void structureSummariesArePerTypeWithoutGeometries() {
        UUID lakeId = UUID.randomUUID();
        stubEmpty(lakeId);
        LakeFeature hump = new LakeFeature();
        hump.setType(FeatureType.HUMP);
        hump.setConfidence(BigDecimal.valueOf(0.8));
        stubReady(lakeId, 1);
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(eq(lakeId), eq(Pipeline.GIS), eq("test")))
                .thenReturn(List.of(hump));
        when(qualityAssembler.averageConfidence(List.of(hump))).thenReturn(0.8);
        when(qualityAssembler.quality(any(), any())).thenReturn(quality(0.8));

        LakeStrategyContext context = builder.build(lake(lakeId, "Head Lake"));
        assertThat(context.structure()).hasSize(FeatureType.values().length);
        assertThat(context.structure()).anyMatch(summary -> summary.type() == FeatureType.HUMP && summary.available() && summary.count() == 1);
        assertThat(objectMapper.valueToTree(context).toString()).doesNotContain("geometry");
    }

    @Test
    void unknownCanonicalSpeciesAreOmittedAndTargetStaysUnconfirmedAtConfirmedSet() {
        UUID lakeId = UUID.randomUUID();
        stubEmpty(lakeId);
        LakeFishSpecies known = new LakeFishSpecies();
        known.setSpecies(FishSpecies.WALLEYE);
        LakeFishSpecies unknown = new LakeFishSpecies();
        unknown.setSpecies(null);
        unknown.setSourceSpeciesName("Mystery fish");
        when(fishSpeciesRepository.findByLakeId(lakeId)).thenReturn(List.of(known, unknown));
        stubReady(lakeId, 0);
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(eq(lakeId), eq(Pipeline.GIS), eq("test")))
                .thenReturn(List.of());

        LakeStrategyContext context = builder.build(lake(lakeId, "Head Lake"));
        assertThat(context.knownSpecies()).containsExactly(FishSpecies.WALLEYE);
        assertThat(builder.confirmedSpecies(context)).contains(FishSpecies.WALLEYE);
        assertThat(builder.confirmedSpecies(context)).doesNotContain(FishSpecies.SMALLMOUTH_BASS);
    }

    private void stubEmpty(UUID lakeId) {
        org.mockito.Mockito.lenient().when(datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(fishSpeciesRepository.findByLakeId(lakeId)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(restrictionRepository.findByLakeId(lakeId)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(eq(lakeId), eq(Pipeline.GIS), eq("test")))
                .thenReturn(List.of());
    }

    private void stubReady(UUID lakeId, long featureCount) {
        when(readinessService.evaluate(lakeId, Pipeline.GIS)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.GIS, StructurePipelineAvailability.READY, "test", featureCount, run(AnalysisRunStatus.READY)));
    }

    private void stubEmptyReady(UUID lakeId) {
        when(readinessService.evaluate(lakeId, Pipeline.GIS)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.GIS, StructurePipelineAvailability.EMPTY, "test", 0, run(AnalysisRunStatus.READY)));
    }

    private Lake lake(UUID id, String name) {
        Lake lake = new Lake();
        lake.setId(id);
        lake.setName(name);
        lake.setProvince("Ontario");
        lake.setMunicipality("Kawartha Lakes");
        lake.setMeanDepthM(BigDecimal.valueOf(5));
        lake.setMaxDepthM(BigDecimal.valueOf(12));
        lake.setTimeZoneId("America/Toronto");
        return lake;
    }

    private LakeDatasetStatus status(UUID lakeId, DatasetType type, DatasetStatusCode code) {
        LakeDatasetStatus status = new LakeDatasetStatus();
        status.setLakeId(lakeId);
        status.setDatasetType(type);
        status.setStatus(code);
        status.setProvider("LIO");
        return status;
    }

    private LakeAnalysisRun run(AnalysisRunStatus status) {
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setStatus(status);
        run.setPipeline(Pipeline.GIS);
        run.setAnalysisVersion("test");
        run.setAlgorithmVersion("1.0.0");
        return run;
    }

    private LakeAnalysisQuality quality(Double average) {
        return new LakeAnalysisQuality(
                "READY", "v1", "READY", 1L, 0, 0, Map.of(), Map.of(), average, Map.of(), List.of(), List.of(), List.of());
    }
}
