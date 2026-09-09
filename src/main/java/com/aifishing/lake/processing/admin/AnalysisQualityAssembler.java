package com.aifishing.lake.processing.admin;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.domain.LakeFeatureStatus;
import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.repo.LakeFeatureStatusRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AnalysisQualityAssembler {

    private final LakeFeatureRepository featureRepository;
    private final LakeFeatureStatusRepository featureStatusRepository;
    private final LakeAnalysisRunRepository analysisRunRepository;
    private final BathymetryContourRepository contourRepository;
    private final BathymetryPointRepository bathymetryPointRepository;
    private final AnalysisContextFactory contextFactory;

    public AnalysisQualityAssembler(
            LakeFeatureRepository featureRepository,
            LakeFeatureStatusRepository featureStatusRepository,
            LakeAnalysisRunRepository analysisRunRepository,
            BathymetryContourRepository contourRepository,
            BathymetryPointRepository bathymetryPointRepository,
            AnalysisContextFactory contextFactory
    ) {
        this.featureRepository = featureRepository;
        this.featureStatusRepository = featureStatusRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.contourRepository = contourRepository;
        this.bathymetryPointRepository = bathymetryPointRepository;
        this.contextFactory = contextFactory;
    }

    public LakeAnalysisQuality quality(Lake lake) {
        return quality(lake, Pipeline.GIS);
    }

    public LakeAnalysisQuality quality(Lake lake, Pipeline pipeline) {
        UUID lakeId = lake.getId();
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        List<LakeFeature> features = featureRepository.findByLakeIdAndPipeline(lakeId, active);
        List<LakeFeatureStatus> statuses = featureStatusRepository.findByLakeIdAndPipelineOrderByFeatureTypeAsc(lakeId, active);
        LakeAnalysisRun run = analysisRunRepository.findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lakeId, active).orElse(null);
        List<String> warnings = warnings(lake, statuses, features);
        return new LakeAnalysisQuality(
                lake.getProcessingStatus(),
                lake.getCurrentAnalysisVersion(),
                run == null ? null : run.getStatus().name(),
                durationMs(run),
                contourRepository.findByLakeId(lakeId).size(),
                bathymetryPointRepository.findByLakeId(lakeId).size(),
                bathymetryAvailability(lakeId),
                countByType(features),
                averageConfidence(features),
                confidenceDistribution(features),
                typeNames(statuses, FeatureStatusCode.NOT_AVAILABLE),
                typeNames(statuses, FeatureStatusCode.FAILED),
                warnings
        );
    }

    public List<FeatureTypeStatusResponse> featureStatuses(UUID lakeId) {
        return featureStatuses(lakeId, Pipeline.GIS);
    }

    public List<FeatureTypeStatusResponse> featureStatuses(UUID lakeId, Pipeline pipeline) {
        Pipeline active = pipeline == null ? Pipeline.GIS : pipeline;
        List<LakeFeature> features = featureRepository.findByLakeIdAndPipeline(lakeId, active);
        Map<FeatureType, List<LakeFeature>> byType = features.stream()
                .collect(Collectors.groupingBy(LakeFeature::getType, () -> new EnumMap<>(FeatureType.class), Collectors.toList()));
        List<LakeFeatureStatus> statuses = featureStatusRepository.findByLakeIdAndPipelineOrderByFeatureTypeAsc(lakeId, active);
        List<FeatureTypeStatusResponse> responses = new ArrayList<>();
        for (LakeFeatureStatus status : statuses) {
            List<LakeFeature> ofType = byType.getOrDefault(status.getFeatureType(), List.of());
            responses.add(new FeatureTypeStatusResponse(
                    status.getFeatureType(),
                    status.getStatus(),
                    status.getRecordCount(),
                    status.getLastAttemptedAt(),
                    status.getLastSuccessfulAnalysisAt(),
                    status.getErrorMessage(),
                    averageConfidence(ofType)
            ));
        }
        return responses;
    }

    public Map<String, Integer> countByType(List<LakeFeature> features) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FeatureType type : FeatureType.values()) {
            counts.put(type.name(), 0);
        }
        for (LakeFeature feature : features) {
            counts.merge(feature.getType().name(), 1, Integer::sum);
        }
        return counts;
    }

    public Double averageConfidence(List<LakeFeature> features) {
        if (features.isEmpty()) {
            return null;
        }
        double sum = features.stream()
                .map(LakeFeature::getConfidence)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        return BigDecimal.valueOf(sum / features.size()).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    public Map<String, Integer> confidenceDistribution(List<LakeFeature> features) {
        Map<String, Integer> buckets = new LinkedHashMap<>();
        buckets.put("0.00-0.25", 0);
        buckets.put("0.25-0.50", 0);
        buckets.put("0.50-0.75", 0);
        buckets.put("0.75-1.00", 0);
        for (LakeFeature feature : features) {
            double value = feature.getConfidence().doubleValue();
            if (value < 0.25) {
                buckets.merge("0.00-0.25", 1, Integer::sum);
            } else if (value < 0.50) {
                buckets.merge("0.25-0.50", 1, Integer::sum);
            } else if (value < 0.75) {
                buckets.merge("0.50-0.75", 1, Integer::sum);
            } else {
                buckets.merge("0.75-1.00", 1, Integer::sum);
            }
        }
        return buckets;
    }

    public Map<String, String> bathymetryAvailability(UUID lakeId) {
        Map<String, String> availability = new LinkedHashMap<>();
        availability.put("BATHYMETRY_INDEX", code(contextFactory.datasetStatus(lakeId, DatasetType.BATHYMETRY_INDEX)));
        availability.put("BATHYMETRY_LINE", code(contextFactory.datasetStatus(lakeId, DatasetType.BATHYMETRY_LINE)));
        availability.put("BATHYMETRY_POINT", code(contextFactory.datasetStatus(lakeId, DatasetType.BATHYMETRY_POINT)));
        return availability;
    }

    private List<String> warnings(Lake lake, List<LakeFeatureStatus> statuses, List<LakeFeature> features) {
        List<String> warnings = new ArrayList<>();
        if (lake.getProcessingStatus() == null) {
            warnings.add("Structure analysis has not been run");
        }
        boolean indexOnly = DatasetStatusCode.AVAILABLE == contextFactory.datasetStatus(lake.getId(), DatasetType.BATHYMETRY_INDEX)
                && contourRepository.findByLakeId(lake.getId()).isEmpty()
                && bathymetryPointRepository.findByLakeId(lake.getId()).isEmpty();
        if (indexOnly) {
            warnings.add("Bathymetry coverage is index-only; depth structures were not interpolated");
        }
        if (features.stream().anyMatch(feature -> feature.getType() == FeatureType.FLAT)) {
            warnings.add("FLAT uses contour spacing/gradient only; no whole-lake slope raster");
        }
        if (features.stream().anyMatch(feature -> feature.getType() == FeatureType.ISLAND_EDGE
                && feature.getDerivationMetadata() != null
                && Boolean.TRUE.equals(feature.getDerivationMetadata().get("geometryOnly")))) {
            warnings.add("ISLAND_EDGE includes geometry-only islands without nearby bathymetry");
        }
        statuses.stream()
                .filter(status -> status.getStatus() == FeatureStatusCode.FAILED)
                .forEach(status -> warnings.add(status.getFeatureType() + " extractor failed; last successful set retained"));
        return warnings;
    }

    private List<String> typeNames(List<LakeFeatureStatus> statuses, FeatureStatusCode code) {
        return statuses.stream()
                .filter(status -> status.getStatus() == code)
                .map(status -> status.getFeatureType().name())
                .toList();
    }

    private Long durationMs(LakeAnalysisRun run) {
        if (run == null || run.getStartedAt() == null || run.getCompletedAt() == null) {
            return null;
        }
        return Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis();
    }

    private String code(DatasetStatusCode status) {
        return status == null ? "NOT_CHECKED" : status.name();
    }
}
