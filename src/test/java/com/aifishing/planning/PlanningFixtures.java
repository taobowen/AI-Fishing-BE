package com.aifishing.planning;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlanningFixtures {

    public static final String ANALYSIS_VERSION = "plan-v1";
    public static final double HEAD_LAT = 44.75;
    public static final double HEAD_LNG = -78.92;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private PlanningFixtures() {
    }

    public static Trip trip(UUID userId, UUID lakeId, FishingMode mode) {
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setLakeId(lakeId);
        trip.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        trip.setSecondaryTargetSpecies(List.of(FishSpecies.WALLEYE));
        trip.setPlannedDate(LocalDate.of(2026, 9, 12));
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        trip.setFishingMode(mode);
        trip.setStatus(TripStatus.DRAFT);
        return trip;
    }

    public static StrategyRun completedStrategy(
            UUID tripId,
            FishingStrategyProfile profile,
            ObjectMapper objectMapper
    ) {
        return completedStrategy(tripId, profile, Pipeline.GIS, ANALYSIS_VERSION, objectMapper);
    }

    public static StrategyRun completedStrategy(
            UUID tripId,
            FishingStrategyProfile profile,
            Pipeline pipeline,
            String analysisVersion,
            ObjectMapper objectMapper
    ) {
        StrategyRun run = new StrategyRun();
        run.setTripId(tripId);
        run.setStatus(StrategyRunStatus.COMPLETED);
        run.setFeaturePipeline(pipeline);
        run.setFeatureAnalysisVersion(analysisVersion);
        run.setStartedAt(Instant.parse("2026-09-02T12:00:00Z"));
        run.setCompletedAt(Instant.parse("2026-09-02T12:00:05Z"));
        run.setStrategyProfile(objectMapper.convertValue(profile, MAP));
        run.setWeatherSnapshot(objectMapper.convertValue(forecast(), MAP));
        return run;
    }

    public static WeatherContext forecast() {
        return new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                14.0,
                10.0,
                240.0,
                0.1,
                70.0,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                List.of(),
                "Air temperature is a forecast of air, not observed lake water temperature."
        );
    }

    public static LakeFeature feature(
            UUID lakeId,
            FeatureType type,
            Polygon geometry,
            double minDepth,
            double maxDepth,
            double confidence,
            String analysisVersion
    ) {
        LakeFeature feature = new LakeFeature();
        feature.setLakeId(lakeId);
        feature.setType(type);
        feature.setPipeline(Pipeline.GIS);
        feature.setGeometry(geometry);
        feature.setMinDepthM(BigDecimal.valueOf(minDepth));
        feature.setMaxDepthM(BigDecimal.valueOf(maxDepth));
        feature.setConfidence(BigDecimal.valueOf(confidence));
        feature.setSourceMethod("GIS");
        feature.setProvider("TEST");
        feature.setAnalysisVersion(analysisVersion);
        return feature;
    }

    public static LakeFeature pointFeature(
            UUID lakeId,
            FeatureType type,
            double lat,
            double lng,
            double minDepth,
            double maxDepth,
            double confidence,
            String analysisVersion
    ) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        LakeFeature feature = feature(
                lakeId,
                type,
                ProcessingFixtures.polygonSquare(lng, lat, 40),
                minDepth,
                maxDepth,
                confidence,
                analysisVersion
        );
        if (type == FeatureType.POINT || type == FeatureType.DROP_OFF) {
            if (type == FeatureType.DROP_OFF) {
                feature.setGeometry(line(lng, lat));
            } else {
                feature.setGeometry(point);
            }
        }
        return feature;
    }

    public static LakeAccessPoint accessPoint(
            UUID lakeId,
            String name,
            double lat,
            double lng,
            boolean boatLaunch,
            boolean shoreAccess
    ) {
        LakeAccessPoint access = new LakeAccessPoint();
        access.setLakeId(lakeId);
        access.setProvider(ProcessingFixtures.PROVIDER);
        access.setSource("TEST");
        access.setImportVersion(ProcessingFixtures.IMPORT_VERSION);
        access.setSourceRecordId(name);
        access.setType(boatLaunch ? "BOAT_LAUNCH" : "SHORE");
        access.setName(name);
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        access.setLocation(point);
        access.setBoatLaunch(boatLaunch);
        access.setShoreAccess(shoreAccess);
        access.setOwnershipType(AccessOwnership.MUNICIPAL);
        return access;
    }

    public static FishingRestriction restriction(
            UUID lakeId,
            String type,
            FishSpecies species,
            Polygon geometry,
            String sourceId
    ) {
        FishingRestriction restriction = new FishingRestriction();
        restriction.setLakeId(lakeId);
        restriction.setProvider(ProcessingFixtures.PROVIDER);
        restriction.setSource("TEST");
        restriction.setImportVersion(ProcessingFixtures.IMPORT_VERSION);
        restriction.setSourceRecordId(sourceId);
        restriction.setRestrictionType(type);
        restriction.setSpecies(species);
        restriction.setGeometry(geometry);
        restriction.setValidFrom(LocalDate.of(2026, 1, 1));
        restriction.setValidTo(LocalDate.of(2026, 12, 31));
        return restriction;
    }

    public static LineString line(double lng, double lat) {
        double dLng = 80 / GeoMetrics.metersPerDegreeLng(lat);
        LineString line = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(lng - dLng, lat),
                new Coordinate(lng + dLng, lat)
        });
        line.setSRID(GeoMapper.SRID);
        return line;
    }

    public static Polygon cShapedPolygon(double lng, double lat, double halfWidthM) {
        double dLat = halfWidthM / GeoMetrics.metersPerDegreeLat();
        double dLng = halfWidthM / GeoMetrics.metersPerDegreeLng(lat);
        double inner = halfWidthM * 0.45;
        double iLat = inner / GeoMetrics.metersPerDegreeLat();
        double iLng = inner / GeoMetrics.metersPerDegreeLng(lat);
        Coordinate[] ring = new Coordinate[]{
                new Coordinate(lng - dLng, lat - dLat),
                new Coordinate(lng + dLng, lat - dLat),
                new Coordinate(lng + dLng, lat + dLat),
                new Coordinate(lng - dLng, lat + dLat),
                new Coordinate(lng - dLng, lat + iLat),
                new Coordinate(lng + iLng, lat + iLat),
                new Coordinate(lng + iLng, lat - iLat),
                new Coordinate(lng - dLng, lat - iLat),
                new Coordinate(lng - dLng, lat - dLat)
        };
        Polygon polygon = FACTORY.createPolygon(FACTORY.createLinearRing(ring));
        polygon.setSRID(GeoMapper.SRID);
        return polygon;
    }

    public static FishingStrategyProfile profile() {
        return StrategyFixtures.validProfile();
    }
}
