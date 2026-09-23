package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.common.geo.WaterDepth;
import com.aifishing.lake.processing.confidence.FeatureConfidenceService;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FeatureFactory {

    public static final String PROVIDER = "DERIVED";

    private final FeatureConfidenceService confidenceService;

    public FeatureFactory(FeatureConfidenceService confidenceService) {
        this.confidenceService = confidenceService;
    }

    public LakeFeature create(
            AnalysisContext context,
            FeatureType type,
            Geometry geometry,
            Double minDepthM,
            Double maxDepthM,
            Double slope,
            Double orientation,
            Double areaM2,
            String sourceMethod,
            String sourceRecordId,
            FeatureEvidence evidence,
            Map<String, Object> extraMetadata
    ) {
        Geometry clipped = type == FeatureType.ISLAND_EDGE
                ? islandGeometry(geometry, context.lakeBoundary())
                : clip(geometry, context.lakeBoundary());
        if (clipped == null) {
            return null;
        }
        clipped.setSRID(GeoMapper.SRID);

        LakeFeature feature = new LakeFeature();
        feature.setLakeId(context.lake().getId());
        feature.setType(type);
        feature.setPipeline(context.pipeline() == null ? com.aifishing.lake.processing.dto.Pipeline.GIS : context.pipeline());
        feature.setGeometry(clipped);
        Double waterMin = WaterDepth.meters(minDepthM);
        Double waterMax = WaterDepth.meters(maxDepthM);
        if (waterMin != null && waterMax != null && waterMin > waterMax) {
            Double swap = waterMin;
            waterMin = waterMax;
            waterMax = swap;
        }
        feature.setMinDepthM(decimal(waterMin, 2));
        feature.setMaxDepthM(decimal(waterMax, 2));
        feature.setSlope(decimal(slope, 6));
        feature.setOrientation(decimal(orientation, 2));
        double area = areaM2 != null ? areaM2 : areaFor(clipped);
        feature.setAreaM2(decimal(area, 2));
        feature.setConfidence(confidenceService.confidence(context.sourceQuality(), evidence));
        feature.setSourceMethod(sourceMethod);
        feature.setProvider(PROVIDER);
        feature.setAnalysisVersion(context.analysisVersion());
        feature.setAnalysisRunId(context.analysisRunId());
        feature.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
        feature.setSourceRecordId(sourceRecordId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidence", evidence.toMap());
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        feature.setDerivationMetadata(metadata);
        return feature;
    }

    /**
     * An island is land. Clipping it to the water polygon would keep only an 8 m rim of a boundary hole.
     * Keep the island when it touches the lake.
     */
    private Geometry islandGeometry(Geometry geometry, Geometry lakeBoundary) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        Geometry working = geometry.copy();
        working.setSRID(GeoMapper.SRID);
        if (lakeBoundary == null || lakeBoundary.isEmpty()) {
            return working;
        }
        Geometry boundary = PolygonalGeometries.of(lakeBoundary);
        if (boundary == null || boundary.isEmpty()) {
            return working;
        }
        double lat = GeoMetrics.referenceLat(boundary);
        Geometry buffered = boundary.buffer(GeoMetrics.bufferDegrees(8, lat));
        try {
            if (working.intersects(buffered) || working.touches(boundary)) {
                return working;
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }

    private Geometry clip(Geometry geometry, Geometry lakeBoundary) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        Geometry working = geometry.copy();
        working.setSRID(GeoMapper.SRID);
        if (lakeBoundary == null || lakeBoundary.isEmpty()) {
            return working;
        }
        Geometry boundary = PolygonalGeometries.of(lakeBoundary);
        if (boundary == null || boundary.isEmpty()) {
            return working;
        }
        double lat = GeoMetrics.referenceLat(boundary);
        Geometry buffered = boundary.buffer(GeoMetrics.bufferDegrees(8, lat));
        if (!working.intersects(buffered)) {
            return null;
        }
        Geometry intersection = working.intersection(buffered);
        if (intersection == null || intersection.isEmpty()) {
            return null;
        }
        intersection = unwrap(intersection);
        intersection.setSRID(GeoMapper.SRID);
        return intersection;
    }

    private Geometry unwrap(Geometry geometry) {
        if (geometry instanceof GeometryCollection collection && collection.getNumGeometries() == 1) {
            Geometry inner = collection.getGeometryN(0).copy();
            inner.setSRID(GeoMapper.SRID);
            return inner;
        }
        return geometry;
    }

    private double areaFor(Geometry geometry) {
        String type = geometry.getGeometryType();
        if ("Polygon".equals(type) || "MultiPolygon".equals(type)) {
            return GeoMetrics.areaM2(geometry);
        }
        return 0;
    }

    private BigDecimal decimal(Double value, int scale) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
