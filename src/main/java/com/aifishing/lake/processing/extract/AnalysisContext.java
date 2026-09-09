package com.aifishing.lake.processing.extract;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Map;

public record AnalysisContext(
        Lake lake,
        String analysisVersion,
        java.util.UUID analysisRunId,
        Pipeline pipeline,
        Map<String, Object> sourceDatasetSnapshot,
        List<BathymetryContour> contours,
        List<BathymetryPoint> bathymetryPoints,
        List<LakeWaterway> shorelines,
        List<LakeWaterway> islands,
        Geometry lakeBoundary,
        LakeSourceQuality sourceQuality,
        List<ContourTopology.ClosedContour> closedContours
) {
    public boolean hasBathymetryGeometry() {
        return !contours.isEmpty() || !bathymetryPoints.isEmpty();
    }

    public boolean requiresBathymetry(FeatureType type) {
        return type == FeatureType.HUMP
                || type == FeatureType.DROP_OFF
                || type == FeatureType.FLAT
                || type == FeatureType.BASIN;
    }
}
