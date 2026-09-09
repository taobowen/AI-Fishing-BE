package com.aifishing.common.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;

class GeoMapperTest {

    private final GeoMapper geoMapper = new GeoMapper();
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void apiLatLngMapsToPostgisLngLat() {
        Point point = geoMapper.toPoint(new GeoPointDto(44.75, -78.92));

        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.getX()).isEqualTo(-78.92);
        assertThat(point.getY()).isEqualTo(44.75);
        assertThat(point.toText()).isEqualTo("POINT (-78.92 44.75)");

        GeoPointDto dto = geoMapper.toDto(point);
        assertThat(dto.lat()).isEqualTo(44.75);
        assertThat(dto.lng()).isEqualTo(-78.92);
    }

    @Test
    void polygonInteriorRingsRoundTripAsHoles() {
        LinearRing shell = factory.createLinearRing(new Coordinate[]{
                new Coordinate(-78.2, 44.1),
                new Coordinate(-78.0, 44.1),
                new Coordinate(-78.0, 44.3),
                new Coordinate(-78.2, 44.3),
                new Coordinate(-78.2, 44.1)
        });
        LinearRing hole = factory.createLinearRing(new Coordinate[]{
                new Coordinate(-78.15, 44.15),
                new Coordinate(-78.12, 44.15),
                new Coordinate(-78.12, 44.18),
                new Coordinate(-78.15, 44.18),
                new Coordinate(-78.15, 44.15)
        });
        Polygon polygon = factory.createPolygon(shell, new LinearRing[]{hole});
        polygon.setSRID(GeoMapper.SRID);
        MultiPolygon multi = factory.createMultiPolygon(new Polygon[]{polygon});
        multi.setSRID(GeoMapper.SRID);

        GeoMultiPolygonDto dto = geoMapper.toDto(multi);
        assertThat(dto.polygons()).hasSize(1);
        assertThat(dto.polygons().getFirst().holes()).hasSize(1);

        MultiPolygon roundTrip = geoMapper.toMultiPolygon(dto);
        Polygon restored = (Polygon) roundTrip.getGeometryN(0);
        assertThat(restored.getNumInteriorRing()).isEqualTo(1);
    }

    @Test
    void multiLineStringRoundTripKeepsLatLng() {
        LineString line = factory.createLineString(new Coordinate[]{
                new Coordinate(-78.92, 44.75),
                new Coordinate(-78.91, 44.76)
        });
        MultiLineString multi = factory.createMultiLineString(new LineString[]{line});
        multi.setSRID(GeoMapper.SRID);

        GeoMultiLineStringDto dto = geoMapper.toDto(multi);
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().getFirst().getFirst().lat()).isEqualTo(44.75);
        assertThat(dto.lines().getFirst().getFirst().lng()).isEqualTo(-78.92);
    }

    @Test
    void mapDtoSimplifiesColinearVertices() {
        Coordinate[] coordinates = new Coordinate[40];
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = new Coordinate(-78.92 + (i * 0.000001), 44.75);
        }
        LineString line = factory.createLineString(coordinates);
        MultiLineString multi = factory.createMultiLineString(new LineString[]{line});
        multi.setSRID(GeoMapper.SRID);

        GeoMultiLineStringDto raw = geoMapper.toDto(multi);
        GeoMultiLineStringDto map = geoMapper.toMapDto(multi);
        assertThat(raw.lines().getFirst()).hasSize(40);
        assertThat(map.lines().getFirst().size()).isLessThan(raw.lines().getFirst().size());
    }
}
