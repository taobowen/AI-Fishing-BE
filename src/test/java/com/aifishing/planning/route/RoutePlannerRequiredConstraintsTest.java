package com.aifishing.planning.route;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningBalance;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.dto.PlanningBalanceResponse;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePlannerRequiredConstraintsTest {

    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @Test
    void everyFeasibleRequiredPointIsOnRouteAndMayBeReordered() {
        PlanningProperties properties = baseProperties();
        properties.getSchedule().setMaxWaypoints(3);
        properties.getSchedule().setMinWaypoints(2);
        var context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID nearId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
        UUID farId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02");
        RankedCandidate near = requiredCandidate(nearId, lng + RoutePlannerHarness.metersToLng(120, lat), lat, 0.55);
        RankedCandidate far = requiredCandidate(farId, lng - RoutePlannerHarness.metersToLng(180, lat), lat, 0.70);
        RoutePlanner.RouteResult result = planner.plan(
                List.of(near, far),
                context,
                RoutePlanConstraints.of(List.of(near.spot(), far.spot()), PlanningMode.CUSTOM));
        assertThat(result.hardConstraintFailure()).isNull();
        assertThat(result.stops()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.stops())
                .extracting(stop -> stop.candidate().spot().getFeatureId())
                .contains(nearId, farId);
    }

    @Test
    void requiredSetInfeasibleWhenSingleRequiredCannotFitWindow() {
        PlanningProperties properties = baseProperties();
        var context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        // Beam-path set failure (pre-check REQUIRED_POINT_INFEASIBLE is covered by validator tests).
        UUID farId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccc03");
        RankedCandidate far = requiredCandidate(
                farId,
                PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(50_000, PlanningFixtures.HEAD_LAT),
                PlanningFixtures.HEAD_LAT,
                0.95);
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setMaxTotalWaitMinutes(0);
        properties.getSchedule().setWaitOptionsMinutes(List.of());
        RoutePlanner.RouteResult result = planner.plan(
                List.of(far),
                context,
                RoutePlanConstraints.of(List.of(far.spot()), PlanningMode.AI));
        assertThat(result.stops()).isEmpty();
        assertThat(result.hardConstraintFailure()).isEqualTo(RoutePlanConstraints.REQUIRED_SET_INFEASIBLE);
    }

    @Test
    void requiredSetInfeasibleWhenEachFitsAloneButNotTogether() {
        PlanningProperties properties = baseProperties();
        properties.getSchedule().setMaxWaypoints(2);
        properties.getSchedule().setMinWaypoints(1);
        properties.getSchedule().setDwellOptionsMinutes(List.of(75));
        properties.getSchedule().setMinSpotMinutes(75);
        properties.getSchedule().setMaxSpotMinutes(75);
        properties.getSchedule().setMaxTotalWaitMinutes(0);
        properties.getSchedule().setWaitOptionsMinutes(List.of());
        properties.getSchedule().setReturnBufferMinutes(10);
        // 90-minute window cannot host two 75-minute HUMP dwells plus travel/return.
        var tripContext = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        tripContext.trip().setFishingStartTime(java.time.LocalTime.of(8, 0));
        tripContext.trip().setFishingEndTime(java.time.LocalTime.of(9, 30));
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID aId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddd04");
        UUID bId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeee05");
        RankedCandidate a = requiredCandidate(aId, lng + RoutePlannerHarness.metersToLng(200, lat), lat, 0.8);
        a.spot().setType(FeatureType.HUMP);
        RankedCandidate b = requiredCandidate(
                bId, lng - RoutePlannerHarness.metersToLng(200, lat), lat, 0.8);
        b.spot().setType(FeatureType.HUMP);
        RoutePlanner.RouteResult aloneA = planner.plan(
                List.of(a), tripContext, RoutePlanConstraints.of(List.of(a.spot()), PlanningMode.CUSTOM));
        RoutePlanner.RouteResult aloneB = planner.plan(
                List.of(b), tripContext, RoutePlanConstraints.of(List.of(b.spot()), PlanningMode.CUSTOM));
        assertThat(aloneA.stops()).isNotEmpty();
        assertThat(aloneB.stops()).isNotEmpty();
        RoutePlanner.RouteResult both = planner.plan(
                List.of(a, b),
                tripContext,
                RoutePlanConstraints.of(List.of(a.spot(), b.spot()), PlanningMode.CUSTOM));
        assertThat(both.stops()).isEmpty();
        assertThat(both.hardConstraintFailure()).isEqualTo(RoutePlanConstraints.REQUIRED_SET_INFEASIBLE);
    }

    @Test
    void customReordersFreelyAmongTemplateCandidates() {
        PlanningProperties properties = baseProperties();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setMinWaypoints(1);
        var context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID weakNear = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffff06");
        UUID strongFar = UUID.fromString("aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaa07");
        RankedCandidate weak = templateCandidate(
                weakNear, lng + RoutePlannerHarness.metersToLng(60, lat), lat, 0.15);
        weak.spot().setType(FeatureType.HUMP);
        RankedCandidate strong = templateCandidate(
                strongFar, lng + RoutePlannerHarness.metersToLng(120, lat), lat, 0.95);
        strong.spot().setType(FeatureType.HUMP);
        RoutePlanner.RouteResult result = planner.plan(
                List.of(weak, strong),
                context,
                RoutePlanConstraints.of(List.of(), PlanningMode.CUSTOM));
        assertThat(result.stops()).isNotEmpty();
        assertThat(result.stops().get(0).candidate().spot().getFeatureId()).isEqualTo(strongFar);
    }

    @Test
    void hybridKeepsUtilityPrimaryAndDoesNotForceWeakTemplate() {
        PlanningProperties properties = baseProperties();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setMinWaypoints(1);
        var context = RoutePlannerHarness.context(calm(), properties, RoutePlannerHarness.launch());
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        UUID strongAi = UUID.fromString("bbbb2222-bbbb-bbbb-bbbb-bbbbbbbbbb08");
        UUID weakTemplate = UUID.fromString("cccc3333-cccc-cccc-cccc-cccccccccc09");
        RankedCandidate ai = aiCandidate(
                strongAi, lng + RoutePlannerHarness.metersToLng(100, lat), lat, 0.92);
        RankedCandidate template = templateCandidate(
                weakTemplate, lng + RoutePlannerHarness.metersToLng(110, lat), lat, 0.20);
        RoutePlanner.RouteResult result = planner.plan(
                List.of(ai, template),
                context,
                RoutePlanConstraints.of(List.of(), PlanningMode.HYBRID));
        assertThat(result.stops()).hasSize(1);
        assertThat(result.stops().get(0).candidate().spot().getFeatureId()).isEqualTo(strongAi);
        assertThat(result.stops().get(0).candidate().spot().getCandidateSource()).isEqualTo(CandidateSource.AI);
    }

    @Test
    void requiredDwellExcludedFromHybridRatioDistance() {
        List<PlannedStop> stops = new ArrayList<>();
        stops.add(stopWith(CandidateSource.REQUIRED, 60));
        stops.add(stopWith(CandidateSource.TEMPLATE, 40));
        stops.add(stopWith(CandidateSource.AI, 40));
        assertThat(RoutePlanConstraints.hybridBalanceDistance(stops)).isZero();
        List<PlannedStop> skewed = List.of(
                stopWith(CandidateSource.REQUIRED, 200),
                stopWith(CandidateSource.TEMPLATE, 10),
                stopWith(CandidateSource.AI, 90));
        assertThat(RoutePlanConstraints.hybridBalanceDistance(skewed)).isGreaterThan(0);
    }

    @Test
    void balanceMinutesUsePlannedDwellOnlyExcludingTransitAndWait() {
        TripWaypoint user = new TripWaypoint();
        user.setCandidateSource(CandidateSource.TEMPLATE);
        user.setPlannedDwellMinutes(30);
        user.setPlannedInternalTransitMinutes(12);
        user.setPlannedWaitMinutes(8);
        user.setEstimatedTravelMinutesFromPrevious(java.math.BigDecimal.valueOf(15));
        TripWaypoint ai = new TripWaypoint();
        ai.setCandidateSource(CandidateSource.AI);
        ai.setPlannedDwellMinutes(25);
        ai.setPlannedInternalTransitMinutes(5);
        ai.setPlannedWaitMinutes(3);
        PlanningBalanceResponse balance = PlanningBalance.fromWaypoints(
                PlanningMode.HYBRID, 0, 1, List.of(user, ai));
        assertThat(balance.userFishingMinutes()).isEqualTo(30);
        assertThat(balance.aiFishingMinutes()).isEqualTo(25);
        assertThat(balance.finalUserStopCount()).isEqualTo(1);
        assertThat(balance.finalAiStopCount()).isEqualTo(1);
    }

    private static PlanningProperties baseProperties() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        properties.getSchedule().setPointDwellMinutes(List.of(20));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSchedule().setMaxSpotMinutes(20);
        return properties;
    }

    private static com.aifishing.strategy.weather.WeatherContext calm() {
        return RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 20, 600),
                RoutePlannerHarness.hour(12, 0, 8, 20, 650),
                RoutePlannerHarness.hour(16, 0, 8, 20, 500)
        ), 8, 20);
    }

    private static RankedCandidate requiredCandidate(UUID id, double lng, double lat, double intrinsic) {
        RankedCandidate ranked = RoutePlannerHarness.candidate(
                id, lng, lat, intrinsic, LightPreference.NEUTRAL, FeatureType.POINT);
        ranked.spot().setCandidateSource(CandidateSource.REQUIRED);
        return ranked;
    }

    private static RankedCandidate templateCandidate(UUID id, double lng, double lat, double intrinsic) {
        RankedCandidate ranked = RoutePlannerHarness.candidate(
                id, lng, lat, intrinsic, LightPreference.NEUTRAL, FeatureType.POINT);
        ranked.spot().setCandidateSource(CandidateSource.TEMPLATE);
        return ranked;
    }

    private static RankedCandidate aiCandidate(UUID id, double lng, double lat, double intrinsic) {
        RankedCandidate ranked = RoutePlannerHarness.candidate(
                id, lng, lat, intrinsic, LightPreference.NEUTRAL, FeatureType.HUMP);
        ranked.spot().setCandidateSource(CandidateSource.AI);
        return ranked;
    }

    private static PlannedStop stopWith(CandidateSource source, int dwell) {
        CandidateSpot spot = new CandidateSpot();
        spot.setCandidateSource(source);
        spot.setFeatureId(UUID.randomUUID());
        spot.setLocation(RoutePlannerHarness.launch());
        RankedCandidate ranked = new RankedCandidate(
                spot,
                new com.aifishing.planning.ranking.SpotScore(0.5, null),
                null);
        return new PlannedStop(
                ranked,
                java.time.Instant.parse("2026-09-12T12:00:00Z"),
                java.time.Instant.parse("2026-09-12T12:00:00Z").plusSeconds(dwell * 60L),
                dwell,
                TravelEstimate.zero(),
                null,
                List.of(),
                java.util.Map.of(),
                0,
                null);
    }
}
