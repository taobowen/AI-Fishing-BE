package com.aifishing.lake.processing;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProcessingFixtures {

    public static final String PROVIDER = "LIO";
    public static final String IMPORT_VERSION = "structure-fixture";

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    private ProcessingFixtures() {
    }

    public static void seedFullStructure(
            UUID lakeId,
            double lat,
            double lng,
            BathymetryContourRepository contourRepository,
            BathymetryPointRepository pointRepository,
            LakeWaterwayRepository waterwayRepository,
            LakeBoundaryRecordRepository boundaryRepository,
            LakeDatasetStatusRepository statusRepository
    ) {
        seedBoundary(lakeId, lat, lng, 700, boundaryRepository);
        seedStatus(lakeId, DatasetType.LAKE_BOUNDARY, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_INDEX, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, 6, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_POINT, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.SHORELINE, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.ISLAND, DatasetStatusCode.AVAILABLE, 1, statusRepository);

        contourRepository.save(contour(lakeId, "hump-outer", 10.0, closedSquare(lng, lat, 350)));
        contourRepository.save(contour(lakeId, "hump-inner", 4.0, closedSquare(lng, lat, 120)));
        double basinLng = lng + metersToLng(220, lat);
        contourRepository.save(contour(lakeId, "basin-outer", 8.0, closedSquare(basinLng, lat, 160)));
        contourRepository.save(contour(lakeId, "basin-inner", 14.0, closedSquare(basinLng, lat, 70)));
        contourRepository.save(contour(lakeId, "drop-deep", 18.0, closedSquare(lng, lat - metersToLat(80), 200)));
        contourRepository.save(contour(lakeId, "drop-shallow", 10.0, closedSquare(lng, lat - metersToLat(80), 175)));
        contourRepository.save(contour(lakeId, "flat-outer", 5.2, closedSquare(lng, lat, 430)));
        contourRepository.save(contour(lakeId, "flat-inner", 5.0, closedSquare(lng, lat, 390)));

        BathymetryPoint point = new BathymetryPoint();
        bind(point, lakeId, "bathy-pt");
        point.setDepthM(BigDecimal.valueOf(6.0));
        point.setLocation(point(lng, lat + metersToLat(320)));
        pointRepository.save(point);

        LakeWaterway shoreline = new LakeWaterway();
        bind(shoreline, lakeId, "shore-1");
        shoreline.setType("SHORELINE");
        shoreline.setGeometry(shorelineWithCape(lng, lat, 520));
        waterwayRepository.save(shoreline);

        LakeWaterway island = new LakeWaterway();
        bind(island, lakeId, "island-1");
        island.setType("ISLAND");
        island.setGeometry(polygonSquare(lng - metersToLng(250, lat), lat - metersToLat(250), 45));
        waterwayRepository.save(island);
    }

    public static void seedShorelineOnly(
            UUID lakeId,
            double lat,
            double lng,
            LakeWaterwayRepository waterwayRepository,
            LakeBoundaryRecordRepository boundaryRepository,
            LakeDatasetStatusRepository statusRepository
    ) {
        seedBoundary(lakeId, lat, lng, 600, boundaryRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_INDEX, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_POINT, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.SHORELINE, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.ISLAND, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        LakeWaterway shoreline = new LakeWaterway();
        bind(shoreline, lakeId, "shore-only");
        shoreline.setType("SHORELINE");
        shoreline.setGeometry(polygonSquare(lng, lat, 400));
        waterwayRepository.save(shoreline);
        LakeWaterway island = new LakeWaterway();
        bind(island, lakeId, "island-only");
        island.setType("ISLAND");
        island.setGeometry(polygonSquare(lng, lat, 40));
        waterwayRepository.save(island);
    }

    public static void seedBathyOnly(
            UUID lakeId,
            double lat,
            double lng,
            BathymetryContourRepository contourRepository,
            LakeBoundaryRecordRepository boundaryRepository,
            LakeDatasetStatusRepository statusRepository
    ) {
        seedBoundary(lakeId, lat, lng, 600, boundaryRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, 2, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_POINT, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.SHORELINE, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.ISLAND, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        contourRepository.save(contour(lakeId, "hump-outer", 10.0, closedSquare(lng, lat, 300)));
        contourRepository.save(contour(lakeId, "hump-inner", 4.0, closedSquare(lng, lat, 110)));
    }

    public static void seedStraightShoreline(
            UUID lakeId,
            double lat,
            double lng,
            LakeWaterwayRepository waterwayRepository,
            LakeDatasetStatusRepository statusRepository
    ) {
        seedStatus(lakeId, DatasetType.SHORELINE, DatasetStatusCode.AVAILABLE, 1, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.BATHYMETRY_POINT, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        seedStatus(lakeId, DatasetType.ISLAND, DatasetStatusCode.NOT_AVAILABLE, 0, statusRepository);
        LakeWaterway shoreline = new LakeWaterway();
        bind(shoreline, lakeId, "straight-shore");
        shoreline.setType("SHORELINE");
        shoreline.setGeometry(polygonSquare(lng, lat, 400));
        waterwayRepository.save(shoreline);
    }

    public static void seedRegulationText(UUID lakeId, FishingRestrictionRepository repository) {
        FishingRestriction restriction = new FishingRestriction();
        bind(restriction, lakeId, "reg-1");
        restriction.setRawText("Sanctuary from the point at 44.75N 78.92W west along the shoreline to a polygon closed at the creek mouth.");
        restriction.setRestrictionType("SANCTUARY");
        restriction.setGeometry(null);
        repository.save(restriction);
    }

    public static void seedStatus(
            UUID lakeId,
            DatasetType type,
            DatasetStatusCode code,
            int recordCount,
            LakeDatasetStatusRepository repository
    ) {
        LakeDatasetStatus status = new LakeDatasetStatus();
        status.setLakeId(lakeId);
        status.setDatasetType(type);
        status.setProvider(PROVIDER);
        status.setStatus(code);
        status.setRecordCount(recordCount);
        repository.save(status);
    }

    public static MultiLineString closedSquare(double lng, double lat, double halfWidthM) {
        Polygon polygon = polygonSquare(lng, lat, halfWidthM);
        LineString ring = polygon.getExteriorRing();
        MultiLineString multiLineString = FACTORY.createMultiLineString(new LineString[]{ring});
        multiLineString.setSRID(GeoMapper.SRID);
        return multiLineString;
    }

    public static Polygon polygonSquare(double lng, double lat, double halfWidthM) {
        double dLat = metersToLat(halfWidthM);
        double dLng = metersToLng(halfWidthM, lat);
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(lng - dLng, lat - dLat),
                new Coordinate(lng + dLng, lat - dLat),
                new Coordinate(lng + dLng, lat + dLat),
                new Coordinate(lng - dLng, lat + dLat),
                new Coordinate(lng - dLng, lat - dLat)
        };
        LinearRing ring = FACTORY.createLinearRing(coordinates);
        Polygon polygon = FACTORY.createPolygon(ring);
        polygon.setSRID(GeoMapper.SRID);
        return polygon;
    }

    public static Polygon shorelineWithCape(double lng, double lat, double halfWidthM) {
        double dLat = metersToLat(halfWidthM);
        double dLng = metersToLng(halfWidthM, lat);
        double capeLng = metersToLng(22, lat);
        double tipLat = lat + dLat - metersToLat(170);
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(new Coordinate(lng - dLng, lat - dLat));
        coordinates.add(new Coordinate(lng + dLng, lat - dLat));
        coordinates.add(new Coordinate(lng + dLng, lat + dLat));
        Coordinate capeEast = new Coordinate(lng + capeLng, lat + dLat);
        Coordinate tip = new Coordinate(lng, tipLat);
        Coordinate capeWest = new Coordinate(lng - capeLng, lat + dLat);
        densify(coordinates, new Coordinate(lng + dLng, lat + dLat), capeEast, 4);
        densify(coordinates, capeEast, tip, 10);
        densify(coordinates, tip, capeWest, 10);
        coordinates.add(new Coordinate(lng - dLng, lat + dLat));
        coordinates.add(new Coordinate(lng - dLng, lat - dLat));
        LinearRing ring = FACTORY.createLinearRing(coordinates.toArray(Coordinate[]::new));
        Polygon polygon = FACTORY.createPolygon(ring);
        polygon.setSRID(GeoMapper.SRID);
        return polygon;
    }

    private static void densify(List<Coordinate> coordinates, Coordinate from, Coordinate to, int steps) {
        if (coordinates.isEmpty() || !coordinates.get(coordinates.size() - 1).equals2D(from)) {
            coordinates.add(from);
        }
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            coordinates.add(new Coordinate(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t
            ));
        }
        coordinates.add(to);
    }

    public static void seedBoundary(
            UUID lakeId,
            double lat,
            double lng,
            double halfWidthM,
            LakeBoundaryRecordRepository boundaryRepository
    ) {
        LakeBoundaryRecord boundary = new LakeBoundaryRecord();
        bind(boundary, lakeId, "boundary-1");
        Polygon polygon = polygonSquare(lng, lat, halfWidthM);
        MultiPolygon multiPolygon = FACTORY.createMultiPolygon(new Polygon[]{polygon});
        multiPolygon.setSRID(GeoMapper.SRID);
        boundary.setGeometry(multiPolygon);
        boundaryRepository.save(boundary);
    }

    private static BathymetryContour contour(UUID lakeId, String sourceId, double depthM, MultiLineString geometry) {
        BathymetryContour contour = new BathymetryContour();
        bind(contour, lakeId, sourceId);
        contour.setDepthM(BigDecimal.valueOf(depthM));
        contour.setGeometry(geometry);
        return contour;
    }

    private static void bind(com.aifishing.lake.ingestion.domain.CanonicalOntarioRecord record, UUID lakeId, String sourceId) {
        record.setLakeId(lakeId);
        record.setProvider(PROVIDER);
        record.setSource("TEST");
        record.setImportVersion(IMPORT_VERSION);
        record.setSourceRecordId(sourceId);
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    private static double metersToLat(double meters) {
        return meters / GeoMetrics.metersPerDegreeLat();
    }

    private static double metersToLng(double meters, double lat) {
        return meters / GeoMetrics.metersPerDegreeLng(lat);
    }
}
