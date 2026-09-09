package com.aifishing.planning.environment;

import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LocalOrientationServiceTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private final LocalOrientationService service = new LocalOrientationService();

    @Test
    void eastAndWestShoresFaceOppositeWater() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        Polygon water = ProcessingFixtures.polygonSquare(lng, lat, 800);
        LakePlanningGeometry geometry = new LakePlanningGeometry(water, List.of());
        CandidateSpot east = shoreSpot(lng + metersToLng(760, lat), lat, FeatureType.POINT, 12.0);
        CandidateSpot west = shoreSpot(lng - metersToLng(760, lat), lat, FeatureType.POINT, 200.0);
        LocalOrientation eastFacing = service.resolve(east, geometry);
        LocalOrientation westFacing = service.resolve(west, geometry);
        assertThat(eastFacing.confidence()).isNotEqualTo(OrientationConfidence.UNKNOWN);
        assertThat(westFacing.confidence()).isNotEqualTo(OrientationConfidence.UNKNOWN);
        assertThat(AzimuthConvention.circularDelta(eastFacing.shorelineWaterFacingAspect(), 270)).isLessThan(35);
        assertThat(AzimuthConvention.circularDelta(westFacing.shorelineWaterFacingAspect(), 90)).isLessThan(35);
        assertThat(eastFacing.rawOrientationUsedAsFacing()).isFalse();
        assertThat(westFacing.rawOrientationUsedAsFacing()).isFalse();
    }

    @Test
    void rawDropOffTangentIsNotTreatedAsWaterFacing() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        Polygon water = ProcessingFixtures.polygonSquare(lng, lat, 800);
        LakePlanningGeometry geometry = new LakePlanningGeometry(water, List.of());
        LineString contour = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(lng - metersToLng(40, lat), lat),
                new Coordinate(lng + metersToLng(40, lat), lat)
        });
        contour.setSRID(4326);
        CandidateSpot drop = shoreSpot(lng, lat, FeatureType.DROP_OFF, 90.0);
        drop.setSourceGeometry(contour);
        LocalOrientation orientation = service.resolve(drop, geometry);
        assertThat(orientation.rawFeatureOrientation()).isCloseTo(90.0, within(0.01));
        assertThat(orientation.rawOrientationUsedAsFacing()).isFalse();
        assertThat(orientation.confidence()).isEqualTo(OrientationConfidence.UNKNOWN);
        assertThat(orientation.facingAspect()).isNull();
    }

    private CandidateSpot shoreSpot(double lng, double lat, FeatureType type, double rawOrientation) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setType(type);
        spot.setLocation(point(lng, lat));
        spot.setRawOrientationDeg(rawOrientation);
        return spot;
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }

    private static double metersToLng(double meters, double lat) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(lat)));
    }
}
