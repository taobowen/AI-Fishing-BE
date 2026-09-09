package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class PointExtractor implements FeatureExtractor {

    private final ProcessingProperties properties;
    private final FeatureFactory featureFactory;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public PointExtractor(ProcessingProperties properties, FeatureFactory featureFactory) {
        this.properties = properties;
        this.featureFactory = featureFactory;
    }

    @Override
    public FeatureType type() {
        return FeatureType.POINT;
    }

    @Override
    public List<LakeFeature> extract(AnalysisContext context) {
        List<Candidate> candidates = new ArrayList<>();
        Point lakeCentroid = context.lake().getCentroid();
        for (LakeWaterway shoreline : context.shorelines()) {
            for (LineString ring : rings(shoreline.getGeometry())) {
                candidates.addAll(candidatesOn(ring, lakeCentroid, shoreline.getSourceRecordId(), context));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::prominenceM).reversed());
        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean tooClose = kept.stream()
                    .anyMatch(existing -> GeoMetrics.distanceM(existing.point, candidate.point)
                            < properties.getPointMinSpacingM());
            if (!tooClose) {
                kept.add(candidate);
            }
        }
        List<LakeFeature> features = new ArrayList<>();
        for (Candidate candidate : kept) {
            FeatureEvidence evidence = new FeatureEvidence(
                    Math.min(1.0, candidate.prominenceM / Math.max(properties.getPointMinProminenceM() * 2.0, 1.0)),
                    candidate.bathymetrySupported,
                    true,
                    null,
                    candidate.bathymetrySupported ? 2 : 1
            );
            LakeFeature feature = featureFactory.create(
                    context,
                    FeatureType.POINT,
                    candidate.point,
                    null,
                    null,
                    null,
                    candidate.orientation,
                    null,
                    "SHORELINE_PROMINENCE",
                    candidate.sourceRecordId,
                    evidence,
                    Map.of("prominenceM", candidate.prominenceM, "bathymetryContinuation", candidate.bathymetrySupported)
            );
            if (feature != null) {
                features.add(feature);
            }
        }
        return features;
    }

    private List<Candidate> candidatesOn(LineString ring, Point lakeCentroid, String sourceRecordId, AnalysisContext context) {
        Coordinate[] coordinates = ring.getCoordinates();
        if (coordinates.length < 5) {
            return List.of();
        }
        double[] cumulative = cumulativeMeters(coordinates);
        double total = cumulative[cumulative.length - 1];
        if (total < properties.getPointWindowM() * 2) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        double minProminence = properties.getPointMinProminenceM();
        double minRatio = properties.getPointMinProminenceRatio();
        for (int i = 1; i < coordinates.length - 1; i++) {
            int backward = indexAtDistance(cumulative, i, -properties.getPointWindowM(), true);
            int forward = indexAtDistance(cumulative, i, properties.getPointWindowM(), true);
            if (backward == i || forward == i || backward == forward) {
                continue;
            }
            Coordinate vertex = coordinates[i];
            Coordinate a = coordinates[backward];
            Coordinate b = coordinates[forward];
            double lat = vertex.y;
            double prominence = pointToSegmentMeters(vertex, a, b, lat);
            if (prominence < minProminence) {
                continue;
            }
            if (prominence / properties.getPointWindowM() < minRatio) {
                continue;
            }
            if (lakeCentroid != null && !jutsTowardCentroid(vertex, a, b, lakeCentroid)) {
                continue;
            }
            Point point = geometryFactory.createPoint(new Coordinate(vertex));
            point.setSRID(GeoMapper.SRID);
            boolean bathy = bathymetryContinues(point, context);
            double orientation = Math.toDegrees(Math.atan2(
                    (b.x - a.x) * GeoMetrics.metersPerDegreeLng(lat),
                    (b.y - a.y) * GeoMetrics.metersPerDegreeLat()
            ));
            candidates.add(new Candidate(point, prominence, (orientation + 360.0) % 360.0, bathy, sourceRecordId));
        }
        return candidates;
    }

    private boolean jutsTowardCentroid(Coordinate vertex, Coordinate a, Coordinate b, Point centroid) {
        Coordinate mid = new Coordinate((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
        double vertexDist = GeoMetrics.distanceM(vertex, new Coordinate(centroid.getX(), centroid.getY()), vertex.y);
        double midDist = GeoMetrics.distanceM(mid, new Coordinate(centroid.getX(), centroid.getY()), vertex.y);
        return vertexDist + 1.0 < midDist;
    }

    private boolean bathymetryContinues(Point point, AnalysisContext context) {
        double bufferM = properties.getPointWindowM();
        for (BathymetryContour contour : context.contours()) {
            if (contour.getGeometry() != null && GeoMetrics.distanceM(point, contour.getGeometry()) <= bufferM) {
                return true;
            }
        }
        for (BathymetryPoint bathymetryPoint : context.bathymetryPoints()) {
            if (bathymetryPoint.getLocation() != null
                    && GeoMetrics.distanceM(point, bathymetryPoint.getLocation()) <= bufferM) {
                return true;
            }
        }
        return false;
    }

    private List<LineString> rings(Geometry geometry) {
        List<LineString> rings = new ArrayList<>();
        if (geometry == null) {
            return rings;
        }
        if (geometry instanceof LineString lineString) {
            rings.add(lineString);
        } else if (geometry instanceof MultiLineString multiLineString) {
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                rings.add((LineString) multiLineString.getGeometryN(i));
            }
        } else if (geometry instanceof Polygon polygon) {
            rings.add(polygon.getExteriorRing());
        } else if (geometry instanceof MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                rings.add(((Polygon) multiPolygon.getGeometryN(i)).getExteriorRing());
            }
        }
        return rings;
    }

    private double[] cumulativeMeters(Coordinate[] coordinates) {
        double[] cumulative = new double[coordinates.length];
        for (int i = 1; i < coordinates.length; i++) {
            cumulative[i] = cumulative[i - 1]
                    + GeoMetrics.distanceM(coordinates[i - 1], coordinates[i], coordinates[i].y);
        }
        return cumulative;
    }

    private int indexAtDistance(double[] cumulative, int from, double deltaM, boolean closed) {
        int n = cumulative.length;
        double total = cumulative[n - 1];
        if (total <= 0) {
            return from;
        }
        if (!closed) {
            if (deltaM >= 0) {
                double target = Math.min(total, cumulative[from] + deltaM);
                for (int i = from; i < n; i++) {
                    if (cumulative[i] >= target) {
                        return i;
                    }
                }
                return n - 1;
            }
            double target = Math.max(0, cumulative[from] + deltaM);
            for (int i = from; i >= 0; i--) {
                if (cumulative[i] <= target) {
                    return i;
                }
            }
            return 0;
        }
        int unique = n - 1;
        double targetAbs = (cumulative[from] + deltaM) % total;
        if (targetAbs < 0) {
            targetAbs += total;
        }
        int best = from % unique;
        double bestErr = Double.POSITIVE_INFINITY;
        for (int i = 0; i < unique; i++) {
            double err = Math.abs(cumulative[i] - targetAbs);
            if (err < bestErr) {
                bestErr = err;
                best = i;
            }
        }
        return best;
    }

    private double pointToSegmentMeters(Coordinate point, Coordinate a, Coordinate b, double lat) {
        double ax = 0;
        double ay = 0;
        double bx = (b.x - a.x) * GeoMetrics.metersPerDegreeLng(lat);
        double by = (b.y - a.y) * GeoMetrics.metersPerDegreeLat();
        double px = (point.x - a.x) * GeoMetrics.metersPerDegreeLng(lat);
        double py = (point.y - a.y) * GeoMetrics.metersPerDegreeLat();
        double length2 = bx * bx + by * by;
        if (length2 == 0) {
            return Math.hypot(px, py);
        }
        double t = Math.max(0, Math.min(1, (px * bx + py * by) / length2));
        double cx = t * bx;
        double cy = t * by;
        return Math.hypot(px - cx, py - cy);
    }

    private record Candidate(
            Point point,
            double prominenceM,
            double orientation,
            boolean bathymetrySupported,
            String sourceRecordId
    ) {
    }
}
