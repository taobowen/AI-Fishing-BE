package com.aifishing.planning.environment;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.PathTraversal;
import com.aifishing.planning.spatial.VisitOptionKey;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TargetSpeciesPriority;
import com.aifishing.strategy.domain.TechniquePreference;
import com.aifishing.strategy.domain.WeatherInterpretation;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArrivalTimeUtilityTest {

    private final TimeAdjustedSpotUtility utility =
            new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty());

    @Test
    void samePhysicalTargetHasDifferentUtilityAt0800And1600WithoutDuplicatingBeamIdentity() {
        UUID targetId = UUID.nameUUIDFromBytes("temporal-basin".getBytes());
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(targetId);
        spot.setFishingTargetId(targetId);
        spot.setType(FeatureType.BASIN);
        spot.setRepresentativeDepthM(7.0);
        spot.setFeatureConfidence(0.8);
        spot.setStrategyWeight(0.5);
        spot.setLocation(RoutePlannerHarness.point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT));
        spot.setPortals(List.of(new VisitPortal("p0", spot.getLocation())));

        PlanningContext context = contextWithSplitDayProfile();
        ScoreBreakdown breakdown = new ScoreBreakdown(0.5, 0.5, 0.8, 0.4, 0.5, 0.5, 0.5, 0.5, 0.0);
        RankedCandidate ranked = new RankedCandidate(spot, new SpotScore(0.5, breakdown), new DepthRange(2, 4));
        VisitPortal portal = spot.getPortals().get(0);
        VisitOptionKey morningKey = VisitOptionKey.of(targetId, null, portal, portal, PathTraversal.FORWARD, null, 0, 0);
        VisitOptionKey afternoonKey = VisitOptionKey.of(targetId, null, portal, portal, PathTraversal.FORWARD, null, 0, 0);

        var weather = TimeIndexedWeather.from(context.weather(), TripClock.zoneId(context));
        LocalOrientation orientation = new LocalOrientationService().resolve(spot, context.geometry());
        ZonedDateTime eight = TripClock.start(context.trip(), context.lake()).withHour(8).withMinute(0);
        ZonedDateTime sixteen = eight.withHour(16);
        double atEight = utility.evaluate(ranked, eight.toInstant(), context, weather, orientation, 0).utility();
        double atSixteen = utility.evaluate(ranked, sixteen.toInstant(), context, weather, orientation, 0).utility();

        assertThat(morningKey.targetOrPhysicalZoneId()).isEqualTo(afternoonKey.targetOrPhysicalZoneId());
        assertThat(morningKey).isEqualTo(afternoonKey);
        assertThat(atSixteen).isGreaterThan(atEight);
    }

    private static PlanningContext contextWithSplitDayProfile() {
        Trip trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), FishingMode.BOAT);
        trip.setFishingStartTime(LocalTime.of(7, 0));
        trip.setFishingEndTime(LocalTime.of(17, 0));
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        lake.setTimeZoneId("America/Toronto");
        Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 1200);
        Point launch = RoutePlannerHarness.launch();
        StrategyRun run = new StrategyRun();
        run.setFeaturePipeline(Pipeline.GIS);
        run.setFeatureAnalysisVersion("plan-v1");
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
                RoutePlannerHarness.hourly(List.of(
                        RoutePlannerHarness.hour(8, 0, 6, 20, 500),
                        RoutePlannerHarness.hour(16, 0, 6, 20, 400)
                ), 6, 20),
                List.of(),
                splitDayProfile(),
                run,
                new PlanningProperties(),
                new ArrayList<>()
        );
    }

    private static FishingStrategyProfile splitDayProfile() {
        return new FishingStrategyProfile(
                List.of(new TargetSpeciesPriority(FishSpecies.SMALLMOUTH_BASS, 1)),
                List.of(),
                List.of(
                        new StrategyTimeWindow(
                                LocalTime.of(7, 0),
                                LocalTime.of(12, 0),
                                new DepthRange(2, 4),
                                List.of(new StructurePreference(FeatureType.HUMP, 0.9, "morning humps")),
                                List.of(new TechniquePreference(TechniqueType.NED_RIG, 0.8, "finesse")),
                                LightPreference.NEUTRAL
                        ),
                        new StrategyTimeWindow(
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0),
                                new DepthRange(6, 9),
                                List.of(new StructurePreference(FeatureType.BASIN, 0.95, "afternoon basin")),
                                List.of(new TechniquePreference(TechniqueType.DROP_SHOT, 0.8, "deep")),
                                LightPreference.NEUTRAL
                        )
                ),
                List.of(),
                new WeatherInterpretation("split day", 0.7),
                0.8,
                null,
                List.of(),
                List.of()
        );
    }
}
