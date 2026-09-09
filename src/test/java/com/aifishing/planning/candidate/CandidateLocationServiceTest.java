package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningFixtures;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateLocationServiceTest {

    private final CandidateLocationService service = new CandidateLocationService();

    @Test
    void polygonUsesInteriorPointNotRawCentroidWhenCentroidIsOutside() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        Polygon cShape = PlanningFixtures.cShapedPolygon(lng, lat, 200);
        LakePlanningGeometry lake = squareLake(lng, lat, 500);
        LakeFeature feature = new LakeFeature();
        feature.setType(FeatureType.HUMP);
        feature.setGeometry(cShape);

        Point chosen = service.locate(feature, lake).orElseThrow();
        Point centroid = cShape.getCentroid();
        assertThat(cShape.covers(chosen)).isTrue();
        assertThat(lake.validFishingPoint(chosen)).isTrue();
        if (!cShape.covers(centroid)) {
            assertThat(chosen.getX()).isNotEqualTo(centroid.getX());
        }
    }

    @Test
    void dropOffUsesLineMidpointOffsetIntoWater() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        LakeFeature feature = new LakeFeature();
        feature.setType(FeatureType.DROP_OFF);
        feature.setGeometry(PlanningFixtures.line(lng, lat));
        LakePlanningGeometry lake = squareLake(lng, lat, 500);

        Point chosen = service.locate(feature, lake).orElseThrow();
        assertThat(lake.validFishingPoint(chosen)).isTrue();
        double fromLine = GeoMetrics.distanceM(feature.getGeometry(), chosen);
        assertThat(fromLine).isGreaterThan(5);
        assertThat(fromLine).isLessThan(40);
    }

    @Test
    void islandEdgePointIsOnWaterNotOnIsland() {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        Polygon water = ProcessingFixtures.polygonSquare(lng, lat, 400);
        Polygon island = ProcessingFixtures.polygonSquare(lng, lat, 40);
        LakePlanningGeometry lake = new LakePlanningGeometry(water.difference(island), List.of(island));
        LakeFeature feature = new LakeFeature();
        feature.setType(FeatureType.ISLAND_EDGE);
        feature.setGeometry(island);

        Point chosen = service.locate(feature, lake).orElseThrow();
        assertThat(lake.inWater(chosen)).isTrue();
        assertThat(lake.onIsland(chosen)).isFalse();
    }

    private LakePlanningGeometry squareLake(double lng, double lat, double halfWidthM) {
        return new LakePlanningGeometry(ProcessingFixtures.polygonSquare(lng, lat, halfWidthM), List.of());
    }
}
