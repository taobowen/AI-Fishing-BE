package com.aifishing.lake.ingestion.geo;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.IngestionAccessProperties;
import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLakeAssociatorTest {

    private static final double LAT = 44.75;
    private static final double LNG = -78.92;
    private static final double HALF_WIDTH_M = 500;

    @Mock
    LakeBoundaryRecordRepository boundaryRepository;

    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private AccessLakeAssociator associator;
    private Lake lake;

    @BeforeEach
    void setUp() {
        IngestionAccessProperties properties = new IngestionAccessProperties();
        properties.setMaxLakeAssociationMeters(300);
        associator = new AccessLakeAssociator(
                new LakeWaterGeometry(boundaryRepository),
                new LocalMetricCrs(),
                properties
        );
        lake = new Lake();
        lake.setId(UUID.randomUUID());
        lake.setCentroid(point(LNG, LAT));
    }

    @Test
    void insideWaterIsAssociatedAtZeroMeters() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, point(LNG, LAT));
        assertThat(association.associated()).isTrue();
        assertThat(association.distanceMeters()).isCloseTo(0, within(1.0));
    }

    @Test
    void sixteenMetersOutsideIsAssociated() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, northOfEdge(16));
        assertThat(association.associated()).isTrue();
        assertThat(association.distanceMeters()).isCloseTo(16, within(3.0));
    }

    @Test
    void twoHundredFiftyNineMetersIsAssociatedAtThreeHundredCap() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, northOfEdge(259));
        assertThat(association.associated()).isTrue();
        assertThat(association.distanceMeters()).isCloseTo(259, within(5.0));
    }

    @Test
    void twentyFourNineteenMetersIsRejected() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, northOfEdge(2419));
        assertThat(association.associated()).isFalse();
        assertThat(association.distanceMeters()).isCloseTo(2419, within(30.0));
    }

    @Test
    void fourteenKilometersIsRejected() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, northOfEdge(14_000));
        assertThat(association.associated()).isFalse();
        assertThat(association.distanceMeters()).isGreaterThan(10_000);
    }

    @Test
    void haliburtonVillageHeadLaunchDoesNotAssociateToCatalogHead() {
        stubSingleBoundary();
        AccessLakeAssociator.Association association = associator.evaluate(lake, point(-78.5135, 45.0452));
        assertThat(association.associated()).isFalse();
    }

    @Test
    void unionedSecondBoundaryAcceptsPointOnSecondPolygon() {
        double eastLng = LNG + 0.05;
        LakeBoundaryRecord first = boundary(ProcessingFixtures.polygonSquare(LNG, LAT, HALF_WIDTH_M));
        LakeBoundaryRecord second = boundary(ProcessingFixtures.polygonSquare(eastLng, LAT, HALF_WIDTH_M));
        when(boundaryRepository.findByLakeId(lake.getId())).thenReturn(List.of(first, second));

        AccessLakeAssociator.Association association = associator.evaluate(lake, point(eastLng, LAT));
        assertThat(association.associated()).isTrue();
        assertThat(association.distanceMeters()).isCloseTo(0, within(1.0));
    }

    @Test
    void noWaterGeometryAssociatesNothing() {
        when(boundaryRepository.findByLakeId(lake.getId())).thenReturn(List.of());
        AccessLakeAssociator.Association association = associator.evaluate(lake, point(LNG, LAT));
        assertThat(association.associated()).isFalse();
    }

    private void stubSingleBoundary() {
        when(boundaryRepository.findByLakeId(lake.getId()))
                .thenReturn(List.of(boundary(ProcessingFixtures.polygonSquare(LNG, LAT, HALF_WIDTH_M))));
    }

    private LakeBoundaryRecord boundary(Polygon polygon) {
        LakeBoundaryRecord record = new LakeBoundaryRecord();
        MultiPolygon multi = factory.createMultiPolygon(new Polygon[]{polygon});
        multi.setSRID(GeoMapper.SRID);
        record.setGeometry(multi);
        return record;
    }

    private Point northOfEdge(double extraMeters) {
        double dLat = (HALF_WIDTH_M + extraMeters) / GeoMetrics.metersPerDegreeLat();
        return point(LNG, LAT + dLat);
    }

    private Point point(double lng, double lat) {
        Point point = factory.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
