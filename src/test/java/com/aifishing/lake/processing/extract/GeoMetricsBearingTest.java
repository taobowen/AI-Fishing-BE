package com.aifishing.lake.processing.extract;

import com.aifishing.common.geo.GeoMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoMetricsBearingTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void northIsZeroAndEastIsNinety() {
        Point origin = point(0, 0);
        assertThat(GeoMetrics.bearingDegrees(origin, point(1, 0))).isCloseTo(0, within(0.5));
        assertThat(GeoMetrics.bearingDegrees(origin, point(0, 1))).isCloseTo(90, within(0.5));
    }

    private static Point point(double lat, double lng) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
