package com.aifishing.lake.processing.service;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.FeatureFactory;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FeatureReplaceService {

    private final LakeFeatureRepository featureRepository;

    public FeatureReplaceService(LakeFeatureRepository featureRepository) {
        this.featureRepository = featureRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replace(UUID lakeId, FeatureType type, List<LakeFeature> features) {
        replace(lakeId, type, Pipeline.GIS, features);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replace(UUID lakeId, FeatureType type, Pipeline pipeline, List<LakeFeature> features) {
        for (LakeFeature feature : features) {
            if (feature.getPipeline() == null) {
                feature.setPipeline(pipeline);
            }
        }
        validateAll(type, pipeline, features);
        featureRepository.deleteUnreferencedByLakeIdAndPipelineAndType(lakeId, pipeline.name(), type.name());
        featureRepository.flush();
        featureRepository.saveAll(features);
        featureRepository.flush();
    }

    private void validateAll(FeatureType type, Pipeline pipeline, List<LakeFeature> features) {
        for (LakeFeature feature : features) {
            if (feature.getType() != type) {
                throw new IllegalStateException("Feature type mismatch: expected " + type + " but was " + feature.getType());
            }
            if (feature.getPipeline() != pipeline) {
                throw new IllegalStateException("Pipeline mismatch: expected " + pipeline + " but was " + feature.getPipeline());
            }
            Geometry geometry = feature.getGeometry();
            if (geometry == null || geometry.isEmpty() || !geometry.isValid()) {
                throw new IllegalStateException("Invalid geometry for " + type);
            }
            if (geometry.getSRID() == 0) {
                geometry.setSRID(GeoMapper.SRID);
            }
            if (geometry.getSRID() != GeoMapper.SRID) {
                throw new IllegalStateException("Geometry SRID must be 4326 for " + type);
            }
            if (feature.getConfidence() == null
                    || feature.getConfidence().compareTo(BigDecimal.ZERO) < 0
                    || feature.getConfidence().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalStateException("Confidence must be between 0 and 1 for " + type);
            }
            if (isBlank(feature.getSourceMethod()) || isBlank(feature.getProvider()) || isBlank(feature.getAnalysisVersion())) {
                throw new IllegalStateException("Provenance fields are required for " + type);
            }
            if (!FeatureFactory.PROVIDER.equals(feature.getProvider())) {
                throw new IllegalStateException("Derived features must use provider DERIVED");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
