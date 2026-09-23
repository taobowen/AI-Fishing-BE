package com.aifishing.planning.route;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.SolarPositionService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.LakeNavRasterBuilder;
import com.aifishing.planning.spatial.SnapshotWaterPathService;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.planning.spatial.ZoneSubPlanner;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.trip.domain.Trip;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RoutePlannerHarness {

    static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    static final String VERSION = "plan-v1";

    private RoutePlannerHarness() {
    }

    public static RoutePlanner planner() {
        TravelTimeEstimator travel = new TravelTimeEstimator();
        TimeAdjustedSpotUtility utility = new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty());
        LocalOrientationService orientation = new LocalOrientationService();
        SpatialUtility spatial = new SpatialUtility(utility);
        ZoneSubPlanner zoneSubPlanner = new ZoneSubPlanner(
                spatial,
                utility,
                orientation,
                new LakeNavRasterBuilder(new com.aifishing.common.geo.LocalMetricCrs()),
                new SnapshotWaterPathService(null)
        );
        return new RoutePlanner(
                travel,
                utility,
                orientation,
                new BoatWeatherPenalty(),
                new VisitOptionFactory(),
                spatial,
                zoneSubPlanner,
                new com.aifishing.planning.search.SearchParameterResolver()
        );
    }

    public static PlanningContext context(WeatherContext weather, PlanningProperties properties, Point launch) {
        Trip trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), FishingMode.BOAT);
        trip.setFishingStartTime(LocalTime.of(8, 0));
        trip.setFishingEndTime(LocalTime.of(16, 0));
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        lake.setTimeZoneId("America/Toronto");
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1200);
        StrategyRun run = new StrategyRun();
        run.setFeaturePipeline(Pipeline.GIS);
        run.setFeatureAnalysisVersion(VERSION);
        AccessResolution access = new AccessResolution(
                AccessResolution.AccessStatus.AUTHORITATIVE, UUID.randomUUID(), "Ramp", launch);
        return new PlanningContext(
                trip,
                lake,
                null,
                access,
                new LakePlanningGeometry(water, List.of()),
                List.of(),
                "AVAILABLE",
                weather,
                List.of(),
                StrategyFixtures.validProfile(),
                run,
                properties,
                new ArrayList<>()
        );
    }

    public static Point launch() {
        return point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT - metersToLat(400));
    }

    public static RankedCandidate candidate(
            UUID id,
            double lng,
            double lat,
            double intrinsic,
            LightPreference light,
            FeatureType type
    ) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(id);
        spot.setType(type);
        spot.setLocation(point(lng, lat));
        spot.setFeatureConfidence(0.8);
        spot.setStrategyWeight(intrinsic);
        spot.setWindowFrom(LocalTime.of(8, 0));
        spot.setWindowTo(LocalTime.of(16, 0));
        spot.setWindowSpecific(true);
        spot.setPipeline(Pipeline.GIS);
        spot.setAnalysisVersion(VERSION);
        spot.setLightPreference(light);
        ScoreBreakdown breakdown = new ScoreBreakdown(intrinsic, 1, 0.8, 1, 0.5, 0.5, 0.5, 0.5, 0.0);
        return new RankedCandidate(spot, new SpotScore(intrinsic, breakdown), new DepthRange(2, 4));
    }

    public static WeatherContext hourly(List<WeatherContext.HourlyWeather> hours, double wind, double cloud) {
        return new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                16.0,
                wind,
                270.0,
                0.0,
                cloud,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                hours,
                "air"
        );
    }

    public static WeatherContext.HourlyWeather hour(int h, int m, double wind, double cloud, double radiation) {
        return new WeatherContext.HourlyWeather(LocalTime.of(h, m), 16.0, wind, 270.0, 0.0, cloud, 1013.0, radiation, radiation);
    }

    public static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }

    public static double metersToLat(double meters) {
        return meters / 111_320.0;
    }

    public static double metersToLng(double meters, double lat) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(lat)));
    }
}
