package com.aifishing.lake.ingestion.processor;

import com.aifishing.common.geo.GeoMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;

class CrsTransformerTest {

    private final CrsTransformer transformer = new CrsTransformer();
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4269);

    @Test
    void transformsNad83ToWgs84() {
        Point nad83 = factory.createPoint(new Coordinate(-78.92, 44.75));
        nad83.setSRID(4269);

        Point wgs84 = (Point) transformer.toWgs84(nad83, 4269);

        assertThat(wgs84.getSRID()).isEqualTo(GeoMapper.SRID);
        assertThat(wgs84.getX()).isBetween(-78.93, -78.91);
        assertThat(wgs84.getY()).isBetween(44.74, 44.76);
    }

    @Test
    void leaves4326Unchanged() {
        Point point = factory.createPoint(new Coordinate(-78.92, 44.75));
        point.setSRID(4326);

        Point result = (Point) transformer.toWgs84(point, 4326);

        assertThat(result.getX()).isEqualTo(-78.92);
        assertThat(result.getY()).isEqualTo(44.75);
        assertThat(result.getSRID()).isEqualTo(4326);
    }
}
