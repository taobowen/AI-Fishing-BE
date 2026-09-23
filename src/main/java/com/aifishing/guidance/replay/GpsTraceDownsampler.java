package com.aifishing.guidance.replay;

import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Thins ordinary ACCEPTED GPS points. Anchor ids are never dropped.
 */
public final class GpsTraceDownsampler {

    static final double ORDINARY_MIN_SPACING_METERS = 25.0;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GpsTraceDownsampler() {
    }

    public static List<GpsPoint> downsample(List<GpsPoint> points, Collection<UUID> anchorIds) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Set<UUID> anchors = anchorIds == null ? Set.of() : new HashSet<>(anchorIds);
        List<GpsPoint> ordered = points.stream()
                .filter(Objects::nonNull)
                .filter(point -> point.id() != null && point.location() != null)
                .toList();
        if (ordered.size() <= 2) {
            return List.copyOf(ordered);
        }
        List<GpsPoint> kept = new ArrayList<>();
        GpsPoint lastOrdinaryKept = null;
        for (int i = 0; i < ordered.size(); i++) {
            GpsPoint point = ordered.get(i);
            boolean firstOrLast = i == 0 || i == ordered.size() - 1;
            boolean anchor = anchors.contains(point.id());
            if (firstOrLast || anchor) {
                kept.add(point);
                if (!anchor) {
                    lastOrdinaryKept = point;
                }
                continue;
            }
            if (lastOrdinaryKept == null
                    || haversineMeters(lastOrdinaryKept.location(), point.location()) >= ORDINARY_MIN_SPACING_METERS) {
                kept.add(point);
                lastOrdinaryKept = point;
            }
        }
        return List.copyOf(kept);
    }

    static double haversineMeters(Point left, Point right) {
        return haversineMeters(left.getY(), left.getX(), right.getY(), right.getX());
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double dLat = latRad2 - latRad1;
        double dLon = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double a = sinLat * sinLat + Math.cos(latRad1) * Math.cos(latRad2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public record GpsPoint(UUID id, Point location) {
    }
}
