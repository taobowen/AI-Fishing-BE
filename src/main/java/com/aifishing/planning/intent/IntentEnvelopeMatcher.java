package com.aifishing.planning.intent;

import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Water-aware shortlist from raw user geometry. Does not read trip strategy, species, or weather.
 */
public final class IntentEnvelopeMatcher {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private IntentEnvelopeMatcher() {
    }

    public static List<IntentMatch> match(
            Geometry raw,
            TemplateTargetKind kind,
            double radiusOrCorridorM,
            int maxMatches,
            List<LakeFishingTarget> catalog,
            LakePlanningGeometry lake
    ) {
        if (raw == null || raw.isEmpty() || kind == TemplateTargetKind.ZONE || maxMatches <= 0) {
            return List.of(IntentMatch.synthetic());
        }
        Geometry envelope = envelope(raw, radiusOrCorridorM, lake);
        if (envelope == null || envelope.isEmpty()) {
            return List.of(IntentMatch.synthetic());
        }
        List<Scored> scored = new ArrayList<>();
        if (catalog != null) {
            for (LakeFishingTarget target : catalog) {
                if (target == null || target.getTargetKind() == TargetKind.ZONE) {
                    continue;
                }
                Scored hit = score(raw, kind, envelope, target, lake);
                if (hit != null) {
                    scored.add(hit);
                }
            }
        }
        if (scored.isEmpty()) {
            return List.of(IntentMatch.synthetic());
        }
        if (kind == TemplateTargetKind.PATH) {
            scored.sort(Comparator
                    .comparingDouble((Scored hit) -> -hit.overlapM)
                    .thenComparingDouble(hit -> hit.distanceM)
                    .thenComparing(hit -> String.valueOf(hit.target.getId())));
        } else {
            scored.sort(Comparator
                    .comparingDouble((Scored hit) -> hit.distanceM)
                    .thenComparing(hit -> String.valueOf(hit.target.getId())));
        }
        int limit = Math.min(maxMatches, scored.size());
        List<IntentMatch> matches = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Scored hit = scored.get(i);
            UUID featureId = hit.target.getSourceFeatureIds() == null || hit.target.getSourceFeatureIds().isEmpty()
                    ? null
                    : hit.target.getSourceFeatureIds().get(0);
            matches.add(new IntentMatch(
                    hit.target.getId(),
                    featureId,
                    i + 1,
                    hit.distanceM,
                    kind == TemplateTargetKind.PATH ? hit.overlapM : null,
                    false
            ));
        }
        return matches;
    }

    private static Scored score(
            Geometry raw,
            TemplateTargetKind kind,
            Geometry envelope,
            LakeFishingTarget target,
            LakePlanningGeometry lake
    ) {
        Geometry feature = target.getGeometry() != null && !target.getGeometry().isEmpty()
                ? target.getGeometry()
                : target.getRepresentativePoint();
        if (feature == null || feature.isEmpty()) {
            return null;
        }
        try {
            if (!envelope.intersects(feature) && (target.getRepresentativePoint() == null
                    || !envelope.covers(target.getRepresentativePoint()))) {
                return null;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        Point featurePoint = target.getRepresentativePoint() != null
                ? target.getRepresentativePoint()
                : feature.getCentroid();
        featurePoint.setSRID(4326);
        Point intentSample = kind == TemplateTargetKind.PATH ? nearestPoint(raw, featurePoint) : asPoint(raw);
        if (intentSample != null && lake != null && lake.landCrossing(intentSample, featurePoint)) {
            return null;
        }
        double distance = GeoMetrics.distanceM(kind == TemplateTargetKind.PATH ? raw : intentSample, featurePoint);
        double overlap = 0;
        if (kind == TemplateTargetKind.PATH && feature.getDimension() >= 1) {
            try {
                overlap = GeoMetrics.lengthM(envelope.intersection(feature));
            } catch (RuntimeException ignored) {
                overlap = 0;
            }
        }
        return new Scored(target, distance, overlap);
    }

    private static Geometry envelope(Geometry raw, double meters, LakePlanningGeometry lake) {
        double lat = raw.getCentroid().getY();
        Geometry buffered = raw.buffer(GeoMetrics.bufferDegrees(meters, lat));
        buffered.setSRID(4326);
        if (lake == null || !lake.hasWater()) {
            return buffered;
        }
        try {
            Geometry clipped = lake.water().intersection(buffered);
            if (clipped == null || clipped.isEmpty()) {
                return null;
            }
            clipped.setSRID(4326);
            return clipped;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Point asPoint(Geometry geometry) {
        if (geometry instanceof Point point && !point.isEmpty()) {
            point.setSRID(4326);
            return point;
        }
        Point centroid = geometry.getCentroid();
        centroid.setSRID(4326);
        return centroid;
    }

    private static Point nearestPoint(Geometry line, Point feature) {
        try {
            Coordinate[] nearest = DistanceOp.nearestPoints(line, feature);
            Point point = FACTORY.createPoint(nearest[0]);
            point.setSRID(4326);
            return point;
        } catch (RuntimeException ignored) {
            return asPoint(line);
        }
    }

    private record Scored(LakeFishingTarget target, double distanceM, double overlapM) {
    }
}
