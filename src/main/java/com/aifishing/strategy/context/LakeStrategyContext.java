package com.aifishing.strategy.context;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.DataLimitation;

import java.util.List;
import java.util.Map;

public record LakeStrategyContext(
        String name,
        String province,
        String region,
        Double meanDepthM,
        Double maxDepthM,
        Map<String, String> datasetCoverage,
        List<FishSpecies> knownSpecies,
        List<StockingSummary> stocking,
        List<String> habitatTypes,
        RegulationCoverage regulations,
        Pipeline featurePipeline,
        PipelineReadiness pipelineReadiness,
        String analysisVersion,
        List<StructureTypeSummary> structure,
        int totalStructureCount,
        Double averageStructureConfidence,
        List<DataLimitation> dataLimitations
) {
    public LakeStrategyContext {
        datasetCoverage = datasetCoverage == null ? Map.of() : Map.copyOf(datasetCoverage);
        knownSpecies = knownSpecies == null ? List.of() : List.copyOf(knownSpecies);
        stocking = stocking == null ? List.of() : List.copyOf(stocking);
        habitatTypes = habitatTypes == null ? List.of() : List.copyOf(habitatTypes);
        structure = structure == null ? List.of() : List.copyOf(structure);
        dataLimitations = dataLimitations == null ? List.of() : List.copyOf(dataLimitations);
    }

    public record StockingSummary(FishSpecies species, Integer year, Integer quantity) {
    }

    public record RegulationCoverage(
            String coverageStatus,
            int structuredRestrictionCount,
            List<String> restrictionTypes
    ) {
        public RegulationCoverage {
            restrictionTypes = restrictionTypes == null ? List.of() : List.copyOf(restrictionTypes);
        }
    }

    public record StructureTypeSummary(
            FeatureType type,
            boolean available,
            int count,
            Double avgConfidence
    ) {
    }
}
