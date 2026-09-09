package com.aifishing.lake.ingestion.processor;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.geo.AccessLakeAssociator;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccessPointProcessorTest {

    @Mock
    AccessLakeAssociator associator;
    @Mock
    LakeAccessPointRepository repository;

    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AccessPointProcessor processor;
    private Lake lake;

    @BeforeEach
    void setUp() {
        processor = new AccessPointProcessor(
                new ProvenanceBinder(objectMapper),
                new CrsTransformer(),
                associator,
                repository
        );
        lake = new Lake();
        lake.setId(UUID.randomUUID());
        lake.setCentroid(point(-78.92, 44.75));
        lenient().when(associator.evaluate(any(), any())).thenAnswer(invocation -> {
            Point location = invocation.getArgument(1);
            if (location == null || location.getX() > -78.5) {
                return new AccessLakeAssociator.Association(false, 14_000);
            }
            return new AccessLakeAssociator.Association(true, 6.0);
        });
    }

    @Test
    void mapsLioBoatLaunchAndRejectsFarPoint() {
        ParsedFeature near = feature(
                "120120",
                -78.91,
                44.75,
                "Boat Launch",
                "Rice Lake Launch",
                "Yes",
                "Municipal"
        );
        ParsedFeature far = feature(
                "120121",
                -78.0,
                44.75,
                "Boat Launch",
                "Rice Lake Far Launch",
                "No",
                "Municipal"
        );

        DatasetProcessor.NormalizeResult result = processor.normalize(lake, "LIO", "v1", List.of(near, far), List.of());

        assertThat(result.records()).hasSize(1);
        LakeAccessPoint mapped = (LakeAccessPoint) result.records().getFirst();
        assertThat(mapped.getType()).isEqualTo("BOAT_LAUNCH");
        assertThat(mapped.getBoatLaunch()).isTrue();
        assertThat(mapped.getShoreAccess()).isNull();
        assertThat(mapped.getName()).isEqualTo("Rice Lake Launch");
        assertThat(mapped.getParking()).isTrue();
        assertThat(mapped.getOwnershipType()).isEqualTo(AccessOwnership.MUNICIPAL);
        assertThat(mapped.getAssociationDistanceMeters()).isEqualTo(6.0);
        assertThat(mapped.getSourceMetadata()).containsEntry("SITE_OWNERSHIP_TYPE", "Municipal");
        assertThat(mapped.getSourceMetadata()).containsEntry("MATERIAL_TYPE", "Concrete");
        assertThat(result.metadata()).containsEntry("rawFeatureCount", 2);
        assertThat(result.metadata()).containsEntry("associatedCount", 1);
        assertThat(result.metadata()).containsEntry("rejectedByDistanceCount", 1);
        @SuppressWarnings("unchecked")
        List<String> rejected = (List<String>) result.metadata().get("rejectedOgfIds");
        assertThat(rejected).contains("120121");
    }

    @Test
    void mapsShorelineAccessWithoutBoatLaunchFlag() {
        ParsedFeature shore = feature(
                "9",
                -78.91,
                44.75,
                "Shoreline Access",
                "Shore walk",
                "Unknown",
                "Provincial"
        );

        LakeAccessPoint mapped = (LakeAccessPoint) processor.normalize(lake, "LIO", "v1", List.of(shore), List.of())
                .records()
                .getFirst();

        assertThat(mapped.getType()).isEqualTo("SHORELINE_ACCESS");
        assertThat(mapped.getShoreAccess()).isTrue();
        assertThat(mapped.getBoatLaunch()).isNull();
        assertThat(mapped.getParking()).isNull();
        assertThat(mapped.getOwnershipType()).isEqualTo(AccessOwnership.PROVINCIAL);
    }

    @Test
    void unknownTypeLeavesFlagsUnsetAndOwnershipUnknown() {
        ParsedFeature unknown = feature(
                "8",
                -78.91,
                44.75,
                "Fishing Platform",
                "Platform",
                "No",
                "Not a real owner"
        );

        LakeAccessPoint mapped = (LakeAccessPoint) processor.normalize(lake, "LIO", "v1", List.of(unknown), List.of())
                .records()
                .getFirst();

        assertThat(mapped.getType()).isEqualTo("FISHING_ACCESS");
        assertThat(mapped.getBoatLaunch()).isNull();
        assertThat(mapped.getShoreAccess()).isNull();
        assertThat(mapped.getOwnershipType()).isEqualTo(AccessOwnership.UNKNOWN);
    }

    @Test
    void privateOwnershipIsNormalized() {
        ParsedFeature feature = feature(
                "7",
                -78.91,
                44.75,
                "Boat Launch",
                "Private ramp",
                "Yes",
                "Private"
        );
        LakeAccessPoint mapped = (LakeAccessPoint) processor.normalize(lake, "LIO", "v1", List.of(feature), List.of())
                .records()
                .getFirst();
        assertThat(mapped.getOwnershipType()).isEqualTo(AccessOwnership.PRIVATE);
    }

    @Test
    void emptyAssociationStillAvailable() {
        org.mockito.Mockito.doReturn(new AccessLakeAssociator.Association(false, 400))
                .when(associator)
                .evaluate(any(), any());
        DatasetProcessor.NormalizeResult result = processor.normalize(
                lake, "LIO", "v1", List.of(feature("1", -78.91, 44.75, "Boat Launch", "None", "Yes", "Municipal")), List.of());
        assertThat(result.records()).isEmpty();
        assertThat(result.recordCount()).isZero();
        assertThat(result.metadata()).containsEntry("associatedCount", 0);
        assertThat(result.metadata()).containsEntry("rejectedByDistanceCount", 1);
    }

    private ParsedFeature feature(
            String ogfId,
            double lng,
            double lat,
            String type,
            String name,
            String parking,
            String ownership
    ) {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("OGF_ID", ogfId);
        properties.put("FISHING_ACCESS_POINT_TYPE", type);
        properties.put("SITE_NAME", name);
        properties.put("PARKING_PRESENCE_FLG", parking);
        properties.put("SITE_OWNERSHIP_TYPE", ownership);
        properties.put("MATERIAL_TYPE", "Concrete");
        properties.put("ACCESSIBILITY_FLG", "Unknown");
        return new ParsedFeature(ogfId, properties, point(lng, lat));
    }

    private Point point(double lng, double lat) {
        Point point = factory.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}
