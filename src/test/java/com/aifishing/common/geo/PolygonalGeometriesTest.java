package com.aifishing.common.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PolygonalGeometriesTest {

    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void flattensMixedCollectionBeforeBuffer() {
        Polygon water = square(-78.25, 44.56, 0.02);
        LineString leftover = factory.createLineString(new Coordinate[]{
                new Coordinate(-78.25, 44.56),
                new Coordinate(-78.23, 44.57)
        });
        Geometry collection = factory.createGeometryCollection(new Geometry[]{water, leftover});

        Geometry polygonal = PolygonalGeometries.of(collection);
        assertThat(polygonal.getGeometryType()).isIn("Polygon", "MultiPolygon");
        assertThatCode(() -> polygonal.buffer(0.0001)).doesNotThrowAnyException();
    }

    @Test
    void overlayResultCollectionCanBeDifferencedAfterFlatten() {
        Polygon water = square(-78.25, 44.56, 0.03);
        LineString leftover = factory.createLineString(new Coordinate[]{
                new Coordinate(-78.25, 44.56),
                new Coordinate(-78.23, 44.57)
        });
        Geometry collection = factory.createGeometryCollection(new Geometry[]{water, leftover});
        Polygon island = square(-78.25, 44.56, 0.005);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> collection.difference(island)
        );
        Geometry polygonal = PolygonalGeometries.of(collection);
        assertThatCode(() -> polygonal.difference(island)).doesNotThrowAnyException();
    }

    private Polygon square(double lng, double lat, double half) {
        return factory.createPolygon(new Coordinate[]{
                new Coordinate(lng - half, lat - half),
                new Coordinate(lng + half, lat - half),
                new Coordinate(lng + half, lat + half),
                new Coordinate(lng - half, lat + half),
                new Coordinate(lng - half, lat - half)
        });
    }
}