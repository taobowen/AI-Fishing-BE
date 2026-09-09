package com.aifishing.strategy.context;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishHabitat;
import com.aifishing.lake.ingestion.domain.FishStockingRecord;
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
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import com.aifishing.strategy.StrategyProperties;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class LakeContextBuilder {

    private final LakeDatasetStatusRepository datasetStatusRepository;
    private final LakeFishSpeciesRepository fishSpeciesRepository;
    private final FishStockingRecordRepository stockingRepository;
    private final FishHabitatRepository habitatRepository;
    private final FishingRestrictionRepository restrictionRepository;
    private final LakeFeatureRepository featureRepository;
    private final AnalysisQualityAssembler qualityAssembler;
    private final StrategyProperties strategyProperties;
    private final StructurePipelineReadinessService readinessService;

    public LakeContextBuilder(
            LakeDatasetStatusRepository datasetStatusRepository,
            LakeFishSpeciesRepository fishSpeciesRepository,
            FishStockingRecordRepository stockingRepository,
            FishHabitatRepository habitatRepository,
            FishingRestrictionRepository restrictionRepository,
            LakeFeatureRepository featureRepository,
            AnalysisQualityAssembler qualityAssembler,
            StrategyProperties strategyProperties,
            StructurePipelineReadinessService readinessService
    ) {
        this.datasetStatusRepository = datasetStatusRepository;
        this.fishSpeciesRepository = fishSpeciesRepository;
        this.stockingRepository = stockingRepository;
        this.habitatRepository = habitatRepository;
        this.restrictionRepository = restrictionRepository;
        this.featureRepository = featureRepository;
        this.qualityAssembler = qualityAssembler;
        this.strategyProperties = strategyProperties;
        this.readinessService = readinessService;
    }

    public LakeStrategyContext build(Lake lake) {
        return build(lake, strategyProperties.getFeaturePipeline());
    }

    public LakeStrategyContext build(Lake lake, Pipeline featurePipeline) {
        UUID lakeId = lake.getId();
        Pipeline pipeline = featurePipeline == null ? strategyProperties.getFeaturePipeline() : featurePipeline;
        Map<String, String> coverage = datasetCoverage(lakeId);
        List<DataLimitation> limitations = new ArrayList<>();
        addDatasetLimitations(coverage, limitations);

        List<FishSpecies> knownSpecies = fishSpeciesRepository.findByLakeId(lakeId).stream()
                .map(LakeFishSpecies::getSpecies)
                .filter(species -> species != null)
                .distinct()
                .toList();
        List<LakeStrategyContext.StockingSummary> stocking = stockingRepository.findByLakeId(lakeId).stream()
                .filter(record -> record.getSpecies() != null)
                .map(record -> new LakeStrategyContext.StockingSummary(
                        record.getSpecies(),
                        record.getStockingYear(),
                        record.getQuantity()
                ))
                .toList();
        List<String> habitatTypes = habitatRepository.findByLakeId(lakeId).stream()
                .map(FishHabitat::getHabitatType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList();

        List<FishingRestriction> restrictions = restrictionRepository.findByLakeId(lakeId);
        String regulationStatus = coverage.getOrDefault(DatasetType.REGULATION.name(), DatasetStatusCode.NOT_CHECKED.name());
        List<String> restrictionTypes = restrictions.stream()
                .map(FishingRestriction::getRestrictionType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList();
        LakeStrategyContext.RegulationCoverage regulationCoverage = new LakeStrategyContext.RegulationCoverage(
                regulationStatus,
                restrictions.size(),
                restrictionTypes
        );

        StructurePipelineReadiness product = readinessService.evaluate(lakeId, pipeline);
        PipelineReadiness readiness = toStrategyReadiness(product);
        if (readiness == PipelineReadiness.FAILED || readiness == PipelineReadiness.NOT_READY) {
            limitations.add(new DataLimitation(
                    DataLimitationCode.STRUCTURE_PIPELINE_NOT_READY,
                    "Structure pipeline " + pipeline + " is " + product.availability() + "; do not invent structure availability."
            ));
        }

        List<LakeFeature> features = product.analysisVersion() == null
                ? List.of()
                : featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(lakeId, pipeline, product.analysisVersion());
        Map<FeatureType, List<LakeFeature>> byType = features.stream()
                .collect(Collectors.groupingBy(LakeFeature::getType, () -> new EnumMap<>(FeatureType.class), Collectors.toList()));
        List<LakeStrategyContext.StructureTypeSummary> structure = new ArrayList<>();
        for (FeatureType type : FeatureType.values()) {
            List<LakeFeature> ofType = byType.getOrDefault(type, List.of());
            Double avg = qualityAssembler.averageConfidence(ofType);
            structure.add(new LakeStrategyContext.StructureTypeSummary(type, !ofType.isEmpty(), ofType.size(), avg));
        }
        Double averageConfidence = qualityAssembler.quality(lake, pipeline).averageConfidence();
        if ((readiness == PipelineReadiness.READY || readiness == PipelineReadiness.PARTIAL) && features.isEmpty()) {
            limitations.add(new DataLimitation(
                    DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS,
                    "Pipeline " + pipeline + " analyzed the lake successfully but produced zero structure features."
            ));
        }
        if (averageConfidence != null && averageConfidence < 0.5) {
            limitations.add(new DataLimitation(
                    DataLimitationCode.STRUCTURE_DATA_LOW_CONFIDENCE,
                    "Mean structure confidence is " + averageConfidence + "."
            ));
        }

        return new LakeStrategyContext(
                lake.getName(),
                lake.getProvince(),
                lake.getMunicipality(),
                lake.getMeanDepthM() == null ? null : lake.getMeanDepthM().doubleValue(),
                lake.getMaxDepthM() == null ? null : lake.getMaxDepthM().doubleValue(),
                coverage,
                knownSpecies,
                stocking,
                habitatTypes,
                regulationCoverage,
                pipeline,
                readiness,
                product.analysisVersion(),
                structure,
                features.size(),
                averageConfidence,
                limitations
        );
    }

    public Set<FishSpecies> confirmedSpecies(LakeStrategyContext lake) {
        Set<FishSpecies> confirmed = new LinkedHashSet<>(lake.knownSpecies());
        for (LakeStrategyContext.StockingSummary row : lake.stocking()) {
            if (row.species() != null) {
                confirmed.add(row.species());
            }
        }
        return confirmed;
    }

    private Map<String, String> datasetCoverage(UUID lakeId) {
        Map<String, String> coverage = new LinkedHashMap<>();
        for (DatasetType type : DatasetType.values()) {
            coverage.put(type.name(), DatasetStatusCode.NOT_CHECKED.name());
        }
        for (LakeDatasetStatus status : datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId)) {
            coverage.put(status.getDatasetType().name(), status.getStatus().name());
        }
        return coverage;
    }

    private void addDatasetLimitations(Map<String, String> coverage, List<DataLimitation> limitations) {
        DatasetStatusCode line = code(coverage.get(DatasetType.BATHYMETRY_LINE.name()));
        DatasetStatusCode point = code(coverage.get(DatasetType.BATHYMETRY_POINT.name()));
        DatasetStatusCode index = code(coverage.get(DatasetType.BATHYMETRY_INDEX.name()));
        if (unavailable(line) && unavailable(point) && unavailable(index)) {
            limitations.add(new DataLimitation(DataLimitationCode.BATHYMETRY_UNAVAILABLE, "Bathymetry datasets are not available."));
        } else if (partial(line) || partial(point) || partial(index) || mixedBathymetry(line, point, index)) {
            limitations.add(new DataLimitation(DataLimitationCode.BATHYMETRY_PARTIAL, "Bathymetry coverage is partial or index-only."));
        }

        DatasetStatusCode vegetation = code(coverage.get(DatasetType.VEGETATION.name()));
        if (unavailable(vegetation)) {
            limitations.add(new DataLimitation(DataLimitationCode.VEGETATION_DATA_UNAVAILABLE, "Vegetation dataset is not available."));
        }
        DatasetStatusCode substrate = code(coverage.get(DatasetType.BOTTOM_SUBSTRATE.name()));
        if (unavailable(substrate)) {
            limitations.add(new DataLimitation(DataLimitationCode.BOTTOM_SUBSTRATE_UNAVAILABLE, "Bottom substrate dataset is not available."));
        }
        DatasetStatusCode habitat = code(coverage.get(DatasetType.FISH_HABITAT.name()));
        if (unavailable(habitat)) {
            limitations.add(new DataLimitation(DataLimitationCode.FISH_HABITAT_UNAVAILABLE, "Fish habitat dataset is not available."));
        } else if (habitat == DatasetStatusCode.PARTIAL) {
            limitations.add(new DataLimitation(DataLimitationCode.FISH_HABITAT_PARTIAL, "Fish habitat coverage is partial."));
        }
        DatasetStatusCode regulation = code(coverage.get(DatasetType.REGULATION.name()));
        if (unavailable(regulation)) {
            limitations.add(new DataLimitation(DataLimitationCode.REGULATION_DATA_UNAVAILABLE, "Regulation dataset is not available."));
        } else if (regulation == DatasetStatusCode.PARTIAL) {
            limitations.add(new DataLimitation(DataLimitationCode.REGULATION_COVERAGE_PARTIAL, "Regulation coverage is partial."));
        }
    }

    private boolean mixedBathymetry(DatasetStatusCode line, DatasetStatusCode point, DatasetStatusCode index) {
        boolean anyAvailable = line == DatasetStatusCode.AVAILABLE || point == DatasetStatusCode.AVAILABLE || index == DatasetStatusCode.AVAILABLE;
        boolean anyMissing = unavailable(line) || unavailable(point);
        return anyAvailable && anyMissing;
    }

    private DatasetStatusCode code(String raw) {
        if (raw == null || raw.isBlank()) {
            return DatasetStatusCode.NOT_CHECKED;
        }
        try {
            return DatasetStatusCode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return DatasetStatusCode.NOT_CHECKED;
        }
    }

    private boolean unavailable(DatasetStatusCode code) {
        return code == null
                || code == DatasetStatusCode.NOT_AVAILABLE
                || code == DatasetStatusCode.NOT_CHECKED
                || code == DatasetStatusCode.FAILED;
    }

    private boolean partial(DatasetStatusCode code) {
        return code == DatasetStatusCode.PARTIAL;
    }

    private PipelineReadiness toStrategyReadiness(StructurePipelineReadiness product) {
        return switch (product.availability()) {
            case READY, EMPTY -> product.run() != null && product.run().getStatus() == AnalysisRunStatus.PARTIAL
                    ? PipelineReadiness.PARTIAL
                    : PipelineReadiness.READY;
            case FAILED -> PipelineReadiness.FAILED;
            case NOT_PROCESSED, STALE, PROVENANCE_INVALID -> PipelineReadiness.NOT_READY;
        };
    }
}
