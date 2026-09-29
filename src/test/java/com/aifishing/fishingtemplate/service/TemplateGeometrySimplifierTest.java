package com.aifishing.fishingtemplate.service;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateGeometrySimplifierTest {

    @Test
    void collapsesColinearFreehandSamples() {
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] coordinates = new Coordinate[40];
        for (int index = 0; index < coordinates.length; index++) {
            coordinates[index] = new Coordinate(-79.5 + index * 0.00001, 44.1);
        }
        LineString line = factory.createLineString(coordinates);
        Geometry simplified = TemplateGeometryValidator.simplifyForStorage(line);
        assertTrue(simplified.getNumPoints() < 8);
        assertEquals(coordinates[0].x, simplified.getCoordinates()[0].x, 1e-9);
    }

    @Test
    void leavesAShortSegmentUntouched() {
        GeometryFactory factory = new GeometryFactory();
        LineString line = factory.createLineString(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(0.01, 0)
        });
        assertEquals(2, TemplateGeometryValidator.simplifyForStorage(line).getNumPoints());
    }
}
