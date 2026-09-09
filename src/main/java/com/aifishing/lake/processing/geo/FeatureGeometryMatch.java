package com.aifishing.lake.processing.geo;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Geometry;

public final class FeatureGeometryMatch {

    public static final double POLYGON_IOU = 0.3;
    public static final double LINE_BUFFER_M = 40;
    public static final double LOCATION_MAX_M = 75;

    private FeatureGeometryMatch() {
    }

    public static boolean matches(FeatureType type, Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return switch (type) {
            case HUMP, FLAT, BASIN, ISLAND_EDGE -> iou(a, b) >= POLYGON_IOU;
            case DROP_OFF -> lineMatches(a, b);
            case POINT -> GeoMetrics.distanceM(a, b) <= LOCATION_MAX_M;
        };
    }

    public static boolean lineMatches(Geometry a, Geometry b) {
        if (bufferedIou(a, b, LINE_BUFFER_M) >= POLYGON_IOU) {
            return true;
        }
        double distance = GeoMetrics.distanceM(a.getCentroid(), b.getCentroid());
        double hausdorff = hausdorffM(a, b);
        return Math.min(distance, hausdorff) <= LOCATION_MAX_M;
    }

    public static double iou(Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        try {
            Geometry intersection = a.intersection(b);
            Geometry union = a.union(b);
            double unionArea = union.getArea();
            if (unionArea <= 0) {
                return 0;
            }
            return intersection.getArea() / unionArea;
        } catch (Exception ex) {
            return 0;
        }
    }

    public static double bufferedIou(Geometry a, Geometry b, double bufferM) {
        double lat = (GeoMetrics.referenceLat(a) + GeoMetrics.referenceLat(b)) / 2.0;
        double deg = GeoMetrics.bufferDegrees(bufferM, lat);
        return iou(a.buffer(deg), b.buffer(deg));
    }

    public static double hausdorffM(Geometry a, Geometry b) {
        try {
            double degrees = DiscreteHausdorffDistance.distance(a, b);
            double lat = (GeoMetrics.referenceLat(a) + GeoMetrics.referenceLat(b)) / 2.0;
            return degrees * ((GeoMetrics.metersPerDegreeLat() + GeoMetrics.metersPerDegreeLng(lat)) / 2.0);
        } catch (Exception ex) {
            return Double.POSITIVE_INFINITY;
        }
    }

    public static double matchQuality(FeatureType type, Geometry a, Geometry b) {
        if (type == FeatureType.POINT || type == FeatureType.DROP_OFF) {
            double distance = GeoMetrics.distanceM(a, b);
            return 1.0 / (1.0 + distance);
        }
        return iou(a, b);
    }
}
