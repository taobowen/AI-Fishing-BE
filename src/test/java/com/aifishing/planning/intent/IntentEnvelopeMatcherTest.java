package com.aifishing.planning.intent;

import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntentEnvelopeMatcherTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double LAT = 44.75;
    private static final double LNG = -78.92;

    @Test
    void pointRanksByDistanceOnlyAndKeepsRawGeometry() {
        Point pin = point(LNG, LAT);
        String before = pin.toText();
        LakeFishingTarget near = feature(TargetKind.POINT, FeatureType.HUMP, point(LNG + east(20), LAT));
        LakeFishingTarget far = feature(TargetKind.POINT, FeatureType.FLAT, point(LNG + east(40), LAT));
        List<IntentMatch> matches = IntentEnvelopeMatcher.match(
                pin,
                TemplateTargetKind.POINT,
                60,
                3,
                List.of(far, near),
                openWater()
        );
        assertThat(pin.toText()).isEqualTo(before);
        assertThat(matches).extracting(IntentMatch::fishingTargetId).containsExactly(near.getId(), far.getId());
        assertThat(matches).allMatch(match -> !match.syntheticFallback());
    }

    @Test
    void pointAcrossIslandIsNotAMatch() {
        Point pin = point(LNG, LAT);
        LakeFishingTarget across = feature(TargetKind.POINT, FeatureType.HUMP, point(LNG + east(40), LAT));
        List<IntentMatch> matches = IntentEnvelopeMatcher.match(
                pin,
                TemplateTargetKind.POINT,
                60,
                3,
                List.of(across),
                waterSplitByIsland()
        );
        assertThat(matches).containsExactly(IntentMatch.synthetic());
    }

    @Test
    void pathPrefersOverlapOverACloserPoint() {
        LineString path = line(LNG, LAT, LNG + east(80), LAT);
        LakeFishingTarget along = feature(
                TargetKind.PATH,
                FeatureType.DROP_OFF,
                line(LNG + east(10), LAT, LNG + east(70), LAT)
        );
        LakeFishingTarget beside = feature(TargetKind.POINT, FeatureType.HUMP, point(LNG + east(5), LAT + north(20)));
        LakeFishingTarget outside = feature(
                TargetKind.POINT,
                FeatureType.HUMP,
                point(LNG + east(400), LAT)
        );
        List<IntentMatch> matches = IntentEnvelopeMatcher.match(
                path,
                TemplateTargetKind.PATH,
                40,
                3,
                List.of(beside, along, outside),
                openWater()
        );
        assertThat(matches.get(0).fishingTargetId()).isEqualTo(along.getId());
        assertThat(matches.get(0).overlapM()).isGreaterThan(0);
        assertThat(matches).extracting(IntentMatch::fishingTargetId).doesNotContain(outside.getId());
    }

    @Test
    void capsTheShortlist() {
        List<LakeFishingTarget> catalog = List.of(
                feature(TargetKind.POINT, FeatureType.HUMP, point(LNG + east(10), LAT)),
                feature(TargetKind.POINT, FeatureType.FLAT, point(LNG + east(20), LAT)),
                feature(TargetKind.POINT, FeatureType.POINT, point(LNG + east(30), LAT)),
                feature(TargetKind.POINT, FeatureType.BASIN, point(LNG + east(40), LAT)),
                feature(TargetKind.POINT, FeatureType.ISLAND_EDGE, point(LNG + east(50), LAT))
        );
        List<IntentMatch> matches = IntentEnvelopeMatcher.match(
                point(LNG, LAT),
                TemplateTargetKind.POINT,
                60,
                3,
                catalog,
                openWater()
        );
        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(IntentMatch::rank).containsExactly(1, 2, 3);
    }

    private static LakePlanningGeometry openWater() {
        return new LakePlanningGeometry(box(LNG - 0.01, LAT - 0.01, LNG + 0.01, LAT + 0.01), List.of());
    }

    private static LakePlanningGeometry waterSplitByIsland() {
        Geometry water = box(LNG - 0.002, LAT - 0.002, LNG + 0.002, LAT + 0.002);
        Geometry island = box(LNG + east(15), LAT - 0.001, LNG + east(25), LAT + 0.001);
        return new LakePlanningGeometry(water, List.of(island));
    }

    private static LakeFishingTarget feature(TargetKind kind, FeatureType type, Geometry geometry) {
        LakeFishingTarget target = new LakeFishingTarget();
        target.setId(UUID.randomUUID());
        target.setTargetKind(kind);
        target.setSemanticType(type);
        target.setGeometry(geometry);
        Point representative = geometry instanceof Point point ? point : geometry.getCentroid();
        representative.setSRID(4326);
        target.setRepresentativePoint(representative);
        target.setSourceFeatureIds(List.of(UUID.randomUUID()));
        return target;
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }

    private static LineString line(double lng1, double lat1, double lng2, double lat2) {
        LineString line = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(lng1, lat1),
                new Coordinate(lng2, lat2)
        });
        line.setSRID(4326);
        return line;
    }

    private static Geometry box(double minLng, double minLat, double maxLng, double maxLat) {
        Geometry polygon = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(minLng, minLat),
                new Coordinate(maxLng, minLat),
                new Coordinate(maxLng, maxLat),
                new Coordinate(minLng, maxLat),
                new Coordinate(minLng, minLat)
        });
        polygon.setSRID(4326);
        return polygon;
    }

    private static double east(double meters) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(LAT)));
    }

    private static double north(double meters) {
        return meters / 111_320.0;
    }
}
