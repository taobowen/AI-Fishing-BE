package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class IslandEdgeExtractor implements FeatureExtractor {

    private final ProcessingProperties properties;
    private final FeatureFactory featureFactory;

    public IslandEdgeExtractor(ProcessingProperties properties, FeatureFactory featureFactory) {
        this.properties = properties;
        this.featureFactory = featureFactory;
    }

    @Override
    public FeatureType type() {
        return FeatureType.ISLAND_EDGE;
    }

    @Override
    public List<LakeFeature> extract(AnalysisContext context) {
        List<LakeFeature> features = new ArrayList<>();
        for (LakeWaterway island : context.islands()) {
            Geometry geometry = island.getGeometry();
            if (geometry == null || geometry.isEmpty()) {
                continue;
            }
            geometry = geometry.copy();
            geometry.setSRID(GeoMapper.SRID);
            double area = GeoMetrics.areaM2(geometry);
            if (area <= 0) {
                area = GeoMetrics.lengthM(geometry);
                if (area < Math.sqrt(properties.getIslandMinAreaM2()) * 4) {
                    continue;
                }
            } else if (area < properties.getIslandMinAreaM2()) {
                continue;
            }
            boolean bathy = bathymetrySupported(geometry, context);
            double prominence = bathy ? 0.7 : 0.35;
            FeatureEvidence evidence = new FeatureEvidence(
                    prominence,
                    bathy,
                    geometry.isValid(),
                    null,
                    bathy ? 2 : 1
            );
            LakeFeature feature = featureFactory.create(
                    context,
                    FeatureType.ISLAND_EDGE,
                    geometry,
                    null,
                    null,
                    null,
                    null,
                    GeoMetrics.areaM2(geometry),
                    "ISLAND_GEOMETRY",
                    island.getSourceRecordId(),
                    evidence,
                    Map.of("bathymetrySupported", bathy, "geometryOnly", !bathy)
            );
            if (feature != null) {
                features.add(feature);
            }
        }
        return features;
    }

    private boolean bathymetrySupported(Geometry island, AnalysisContext context) {
        double lat = GeoMetrics.referenceLat(island);
        double bufferDeg = GeoMetrics.bufferDegrees(properties.getIslandBathyBufferM(), lat);
        Geometry search = island.buffer(bufferDeg);
        for (BathymetryContour contour : context.contours()) {
            if (contour.getGeometry() != null && search.intersects(contour.getGeometry())) {
                return true;
            }
        }
        for (BathymetryPoint point : context.bathymetryPoints()) {
            if (point.getLocation() != null && search.intersects(point.getLocation())) {
                return true;
            }
        }
        return false;
    }
}
