package com.aifishing.lake.ingestion.admin;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LakeBootstrapValidationIT extends AbstractIntegrationTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Autowired
    LakeFeatureRepository featureRepository;

    @Test
    void reportsTypeAwareGeometryWarningsWithoutDeletingRows() throws Exception {
        var lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        Polygon water = ProcessingFixtures.polygonSquare(-78.92, 44.75, 700);
        var factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        var multi = factory.createMultiPolygon(new Polygon[]{water});
        multi.setSRID(GeoMapper.SRID);
        lake.setBoundary(multi);
        lakeRepository.save(lake);

        Polygon tiny = ProcessingFixtures.polygonSquare(-78.92, 44.75, 40);
        featureRepository.save(feature(FeatureType.HUMP, tiny, BigDecimal.valueOf(0.8)));
        featureRepository.save(feature(FeatureType.HUMP, tiny, BigDecimal.valueOf(0.7)));
        featureRepository.save(feature(FeatureType.HUMP, ProcessingFixtures.polygonSquare(-78.92, 44.75, 20_000), BigDecimal.valueOf(0.6)));
        featureRepository.save(feature(FeatureType.DROP_OFF, longLine(), BigDecimal.valueOf(0.5)));
        featureRepository.save(feature(FeatureType.POINT, originPoint(), BigDecimal.valueOf(0.9)));

        long before = featureRepository.count();

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/bootstrap-validation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postgisAvailable", is(true)))
                .andExpect(jsonPath("$.empiricalColdStart", is(true)))
                .andExpect(jsonPath("$.geometry.polygonAbsurdAreaCount", greaterThan(0)))
                .andExpect(jsonPath("$.geometry.lineAbsurdLengthCount", greaterThan(0)))
                .andExpect(jsonPath("$.geometry.pointGeometriesSkippedForArea", is(1)))
                .andExpect(jsonPath("$.geometry.exactDuplicateGroupCount", greaterThan(0)))
                .andExpect(jsonPath("$.geometry.polygonAbsurdAreaCount").value(greaterThan(0)));

        org.assertj.core.api.Assertions.assertThat(featureRepository.count()).isEqualTo(before);
    }

    private LakeFeature feature(FeatureType type, org.locationtech.jts.geom.Geometry geometry, BigDecimal confidence) {
        LakeFeature feature = PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                type == FeatureType.POINT ? FeatureType.HUMP : type,
                ProcessingFixtures.polygonSquare(-78.92, 44.75, 40),
                3,
                8,
                confidence.doubleValue(),
                "v-test"
        );
        feature.setId(UUID.randomUUID());
        feature.setType(type);
        feature.setPipeline(Pipeline.GIS);
        geometry.setSRID(GeoMapper.SRID);
        feature.setGeometry(geometry);
        return feature;
    }

    private LineString longLine() {
        LineString line = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(-80.0, 44.0),
                new Coordinate(-70.0, 44.0)
        });
        line.setSRID(GeoMapper.SRID);
        return line;
    }

    private Point originPoint() {
        Point point = FACTORY.createPoint(new Coordinate(-78.92, 44.75));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
