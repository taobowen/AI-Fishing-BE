package com.aifishing.launch;

import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CustomLaunchResolverTest {

    private static final double LAT = PlanningFixtures.HEAD_LAT;
    private static final double LNG = PlanningFixtures.HEAD_LNG;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    private final CustomLaunchResolver resolver = new CustomLaunchResolver(new LocalMetricCrs(), new LaunchProperties());

    @Test
    void landNearShoreSnapsWithinMetersAndAnchorsInWater() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 200);
        Point requested = offset(0, 250);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(water)).orElseThrow();
        assertThat(resolution.snapDistanceMeters()).isCloseTo(50, within(8.0));
        assertThat(geometry(water).inWater(resolution.routeStartPoint())).isTrue();
        assertThat(resolver.meterDistance(resolution.shoreAccessPoint(), resolution.routeStartPoint()))
                .isBetween(2.5, 16.0);
        assertThat(resolution.shorelineKind()).isEqualTo(ShorelineKind.MAINLAND);
    }

    @Test
    void inWaterNearShoreStillSnapsToBoundary() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 200);
        Point requested = offset(0, 0);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(water)).orElseThrow();
        assertThat(resolution.snapDistanceMeters()).isCloseTo(200, within(8.0));
        assertThat(geometry(water).inWater(resolution.routeStartPoint())).isTrue();
    }

    @Test
    void inWaterFarFromShoreStillResolvesWithoutLandMaxSnap() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 400);
        Point requested = offset(0, 0);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(water)).orElseThrow();
        assertThat(resolution.snapDistanceMeters()).isGreaterThan(250);
        assertThat(geometry(water).inWater(resolution.routeStartPoint())).isTrue();
    }

    @Test
    void tooFarFromShoreFails() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 80);
        Point requested = offset(0, 400);
        assertThat(resolver.resolve(requested, geometry(water))).isEmpty();
        assertThat(resolver.failureCode(requested, geometry(water)))
                .isEqualTo(CustomLaunchResolver.FailureCode.TOO_FAR);
    }

    @Test
    void missingWaterFails() {
        Point requested = offset(0, 0);
        assertThat(resolver.resolve(requested, new LakePlanningGeometry(null, List.of()))).isEmpty();
        assertThat(resolver.failureCode(requested, new LakePlanningGeometry(null, List.of())))
                .isEqualTo(CustomLaunchResolver.FailureCode.MISSING_WATER);
    }

    @Test
    void multiPolygonKeepsRouteStartOnCoveringComponent() {
        Polygon west = ProcessingFixtures.polygonSquare(LNG - 0.01, LAT, 120);
        Polygon east = ProcessingFixtures.polygonSquare(LNG + 0.01, LAT, 120);
        MultiPolygon water = FACTORY.createMultiPolygon(new Polygon[]{west, east});
        water.setSRID(GeoMapper.SRID);
        Point requested = east.getInteriorPoint();
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(water)).orElseThrow();
        assertThat(east.covers(resolution.routeStartPoint()) || east.distance(resolution.routeStartPoint()) < 1e-6)
                .isTrue();
        assertThat(west.covers(resolution.routeStartPoint())).isFalse();
    }

    @Test
    void islandInteriorRingWarnsButAllows() {
        Polygon outer = ProcessingFixtures.polygonSquare(LNG, LAT, 300);
        Polygon hole = ProcessingFixtures.polygonSquare(LNG, LAT, 60);
        Polygon water = FACTORY.createPolygon(outer.getExteriorRing(), new LinearRing[]{hole.getExteriorRing()});
        water.setSRID(GeoMapper.SRID);
        Point requested = offset(0, 60);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(water, hole)).orElseThrow();
        assertThat(resolution.warnings()).contains(CustomLaunchResolver.ISLAND_SHORE);
        assertThat(resolution.shorelineKind()).isEqualTo(ShorelineKind.ISLAND_OR_INTERIOR_RING);
        assertThat(geometry(water, hole).inWater(resolution.routeStartPoint())).isTrue();
    }

    @Test
    void narrowBayUsesFallbackOffsetInsteadOfCentroid() {
        Polygon bay = ProcessingFixtures.polygonSquare(LNG, LAT, 12);
        Point requested = offset(0, 12);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(requested, geometry(bay)).orElseThrow();
        assertThat(geometry(bay).inWater(resolution.routeStartPoint())).isTrue();
        assertThat(resolution.routeStartPoint().equals(bay.getCentroid())).isFalse();
    }

    @Test
    void officialOnLandDoesNotMutateCanonicalCoordinates() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 200);
        Point official = offset(0, 220);
        LakeAccessPoint point = PlanningFixtures.accessPoint(UUID.randomUUID(), "Ramp", official.getY(), official.getX(), true, false);
        CustomLaunchResolver.Resolution resolution = resolver.resolve(point.getLocation(), geometry(water)).orElseThrow();
        assertThat(point.getLocation().getY()).isEqualTo(official.getY());
        assertThat(point.getLocation().getX()).isEqualTo(official.getX());
        assertThat(geometry(water).inWater(resolution.routeStartPoint())).isTrue();
    }

    @Test
    void nearbyOfficialDoesNotChangeMode() {
        Polygon water = ProcessingFixtures.polygonSquare(LNG, LAT, 250);
        LakeAccessPoint nearby = PlanningFixtures.accessPoint(UUID.randomUUID(), "Near", LAT, LNG, true, false);
        LakeAccessPoint far = PlanningFixtures.accessPoint(
                UUID.randomUUID(), "Far", LAT + 0.05, LNG, true, false);
        var suggestions = resolver.nearbyOfficial(offset(0, 0), List.of(nearby, far), geometry(water));
        assertThat(suggestions).extracting(com.aifishing.launch.api.CustomLaunchPreviewResponse.NearbyOfficialSuggestion::name)
                .containsExactly("Near");
    }

    private static LakePlanningGeometry geometry(org.locationtech.jts.geom.Geometry water) {
        return new LakePlanningGeometry(water, List.of());
    }

    private static LakePlanningGeometry geometry(org.locationtech.jts.geom.Geometry water, Polygon island) {
        return new LakePlanningGeometry(water, List.of(island));
    }

    private static Point offset(double eastM, double northM) {
        double lat = LAT + northM / 111_320.0;
        double lng = LNG + eastM / (111_320.0 * Math.cos(Math.toRadians(LAT)));
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
