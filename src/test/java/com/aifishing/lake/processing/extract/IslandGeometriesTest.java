package com.aifishing.lake.processing.extract;

import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IslandGeometriesTest {

    @Test
    void interiorRingAboveMinimumBecomesAnIsland() {
        Polygon hole = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40);
        Polygon water = waterWithHole(hole);
        List<Geometry> islands = IslandGeometries.includingInteriorRings(List.of(), water, 150);
        assertThat(islands).hasSize(1);
        assertThat(GeoMetrics.areaM2(islands.get(0))).isGreaterThan(150);
    }

    @Test
    void tinyHoleAndCoveredRingAreSkipped() {
        Polygon tiny = ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG + 0.001,
                PlanningFixtures.HEAD_LAT,
                5
        );
        Polygon covered = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40);
        Polygon water = waterWithHole(covered);
        List<Geometry> islands = IslandGeometries.includingInteriorRings(List.of(covered), water, 150);
        assertThat(islands).hasSize(1);
        assertThat(IslandGeometries.includingInteriorRings(List.of(), waterWithHole(tiny), 150)).isEmpty();
    }

    private static Polygon waterWithHole(Polygon hole) {
        Polygon outer = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 800);
        Geometry holed = outer.difference(hole);
        assertThat(holed).isInstanceOf(Polygon.class);
        return (Polygon) holed;
    }
}
