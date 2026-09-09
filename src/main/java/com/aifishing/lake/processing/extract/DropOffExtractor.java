package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DropOffExtractor implements FeatureExtractor {

    private final ProcessingProperties properties;
    private final ContourTopology topology;
    private final FeatureFactory featureFactory;

    public DropOffExtractor(
            ProcessingProperties properties,
            ContourTopology topology,
            FeatureFactory featureFactory
    ) {
        this.properties = properties;
        this.topology = topology;
        this.featureFactory = featureFactory;
    }

    @Override
    public FeatureType type() {
        return FeatureType.DROP_OFF;
    }

    @Override
    public List<LakeFeature> extract(AnalysisContext context) {
        List<ContourLine> lines = new ArrayList<>();
        for (BathymetryContour contour : context.contours()) {
            if (contour.getDepthM() == null) {
                continue;
            }
            double depth = contour.getDepthM().doubleValue();
            for (LineString line : topology.allLines(List.of(contour))) {
                if (line == null || line.isEmpty() || line.getLength() == 0) {
                    continue;
                }
                line.setSRID(GeoMapper.SRID);
                lines.add(new ContourLine(line, depth, contour.getSourceRecordId()));
            }
        }

        Map<String, Candidate> best = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                ContourLine a = lines.get(i);
                ContourLine b = lines.get(j);
                if (a.depthM == b.depthM) {
                    continue;
                }
                double spacing = GeoMetrics.distanceM(a.line, b.line);
                if (spacing <= 0 || spacing > properties.getDropoffMaxSpacingM()) {
                    continue;
                }
                double relief = Math.abs(a.depthM - b.depthM);
                double gradient = relief / spacing;
                if (gradient < properties.getDropoffMinGradient()) {
                    continue;
                }
                ContourLine deeper = a.depthM >= b.depthM ? a : b;
                String key = locationKey(deeper.line);
                Candidate existing = best.get(key);
                if (existing == null || gradient > existing.gradient) {
                    best.put(key, new Candidate(deeper, Math.min(a.depthM, b.depthM), Math.max(a.depthM, b.depthM),
                            spacing, relief, gradient));
                }
            }
        }

        List<LakeFeature> features = new ArrayList<>();
        for (Candidate candidate : best.values()) {
            double prominence = Math.min(1.0, candidate.gradient / Math.max(properties.getDropoffMinGradient() * 3.0, 0.01));
            FeatureEvidence evidence = new FeatureEvidence(
                    prominence,
                    true,
                    candidate.line.line.isValid(),
                    null,
                    context.bathymetryPoints().isEmpty() ? 1 : 2
            );
            LakeFeature feature = featureFactory.create(
                    context,
                    FeatureType.DROP_OFF,
                    candidate.line.line,
                    candidate.minDepth,
                    candidate.maxDepth,
                    candidate.gradient,
                    orientation(candidate.line.line),
                    null,
                    "CONTOUR_GRADIENT",
                    candidate.line.sourceRecordId,
                    evidence,
                    Map.of(
                            "spacingM", candidate.spacing,
                            "reliefM", candidate.relief,
                            "gradient", candidate.gradient
                    )
            );
            if (feature != null) {
                features.add(feature);
            }
        }
        return features;
    }

    private String locationKey(LineString line) {
        var centroid = line.getCentroid();
        double lat = centroid.getY();
        double meters = 25;
        double dLat = meters / GeoMetrics.metersPerDegreeLat();
        double dLng = meters / GeoMetrics.metersPerDegreeLng(lat);
        long x = Math.round(centroid.getX() / dLng);
        long y = Math.round(centroid.getY() / dLat);
        return x + ":" + y;
    }

    private Double orientation(LineString line) {
        Coordinate[] coordinates = line.getCoordinates();
        if (coordinates.length < 2) {
            return null;
        }
        Coordinate start = coordinates[0];
        Coordinate end = coordinates[coordinates.length - 1];
        double dLat = (end.y - start.y) * GeoMetrics.metersPerDegreeLat();
        double dLng = (end.x - start.x) * GeoMetrics.metersPerDegreeLng((start.y + end.y) / 2.0);
        double degrees = Math.toDegrees(Math.atan2(dLng, dLat));
        return (degrees + 360.0) % 360.0;
    }

    private record ContourLine(LineString line, double depthM, String sourceRecordId) {
    }

    private record Candidate(
            ContourLine line,
            double minDepth,
            double maxDepth,
            double spacing,
            double relief,
            double gradient
    ) {
    }
}
