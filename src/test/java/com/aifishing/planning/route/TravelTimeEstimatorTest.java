package com.aifishing.planning.route;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeEstimatorTest {

    private final TravelTimeEstimator estimator = new TravelTimeEstimator();
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void landCrossingUsesHigherDetourNotNaiveStraightLine() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        Polygon water = ProcessingFixtures.polygonSquare(lng, lat, 400);
        Polygon island = ProcessingFixtures.polygonSquare(lng, lat, 80);
        LakePlanningGeometry geometry = new LakePlanningGeometry(water, List.of(island));
        Point west = point(lng - metersToLng(200, lat), lat);
        Point east = point(lng + metersToLng(200, lat), lat);
        PlanningProperties properties = new PlanningProperties();

        TravelEstimate estimate = estimator.estimate(
                west, east, geometry, properties, FishingMode.BOAT, 12.0);
        TravelEstimate naive = estimator.estimate(
                west, east, new LakePlanningGeometry(water, List.of()), properties, FishingMode.BOAT, 12.0);

        assertThat(estimate.landCrossingDetected()).isTrue();
        assertThat(estimate.appliedDetourFactor()).isEqualTo(properties.getTravel().getLandCrossingDetourFactor());
        assertThat(estimate.minutes()).isGreaterThan(naive.minutes());
    }

    private Point point(double lng, double lat) {
        Point point = factory.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }

    private static double metersToLng(double meters, double lat) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(lat)));
    }
}
