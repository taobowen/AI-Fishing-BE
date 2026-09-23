package com.aifishing.planning.validation;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.filter.RegulationFilter;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.MacroVisitKind;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripPlanValidatorTest {

    private final TripPlanValidator validator = new TripPlanValidator(new RegulationFilter());

    @Test
    void returnAfterDeadlineFails() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 400)), 8, 20),
                new com.aifishing.planning.PlanningProperties(),
                RoutePlannerHarness.launch());
        Instant start = TripClock.startAt(context);
        Instant end = TripClock.endAt(context);
        PlannedStop stop = stop(start.plus(Duration.ofMinutes(30)), 30);
        RoutePlanner.RouteResult lateReturn = new RoutePlanner.RouteResult(
                List.of(stop),
                false,
                start,
                end.plusSeconds(120),
                TravelEstimate.zero(),
                List.of(),
                0,
                30,
                5,
                0,
                0);
        assertThat(validator.validate(List.of(stop), context, lateReturn))
                .isEqualTo("VALIDATION_FAILED: return after deadline");
        RoutePlanner.RouteResult onTime = new RoutePlanner.RouteResult(
                List.of(stop),
                false,
                start,
                end.minusSeconds(60),
                TravelEstimate.zero(),
                List.of(),
                0,
                30,
                5,
                0,
                0);
        assertThat(validator.validate(List.of(stop), context, onTime)).isNull();
    }

    @Test
    void waitCapExceededFails() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 400)), 8, 20),
                new com.aifishing.planning.PlanningProperties(),
                RoutePlannerHarness.launch());
        Instant start = TripClock.startAt(context);
        PlannedStop stop = new PlannedStop(
                RoutePlannerHarness.candidate(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8"),
                        -78.92,
                        44.75,
                        0.6,
                        LightPreference.NEUTRAL,
                        FeatureType.HUMP),
                start.plus(Duration.ofMinutes(90)),
                start.plus(Duration.ofMinutes(120)),
                30,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                75,
                "LAUNCH");
        assertThat(validator.validate(List.of(stop), context)).isEqualTo("VALIDATION_FAILED: wait cap exceeded");
    }

    @Test
    void duplicatePointVisitFails() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        PlannedStop first = stop(start.plus(Duration.ofMinutes(30)), 30);
        PlannedStop second = stop(start.plus(Duration.ofMinutes(90)), 30);
        assertThat(validator.validate(List.of(first, second), context))
                .isEqualTo("VALIDATION_FAILED: duplicate visit");
    }

    @Test
    void repeatedZoneWithDisjointPackagesPasses() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        UUID zoneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID a1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID a2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        UUID a3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
        UUID a4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
        PlannedStop first = zoneStop(
                start.plus(Duration.ofMinutes(30)),
                30,
                zoneId,
                MacroVisitKind.NEW_ZONE_VISIT,
                pkg(a1, a2));
        PlannedStop second = zoneStop(
                start.plus(Duration.ofMinutes(90)),
                30,
                zoneId,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                pkg(a3, a4));
        assertThat(validator.validate(List.of(first, second), context)).isNull();
    }

    @Test
    void abacRouteWithDisjointZonePackagesPasses() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        UUID zoneA = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID a1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID a2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        UUID a3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
        UUID a4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
        PlannedStop firstA = zoneStop(
                start.plus(Duration.ofMinutes(20)),
                20,
                zoneA,
                MacroVisitKind.NEW_ZONE_VISIT,
                pkg(a1, a2));
        PlannedStop pointB = pointStop(
                start.plus(Duration.ofMinutes(50)),
                20,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa0b"));
        PlannedStop secondA = zoneStop(
                start.plus(Duration.ofMinutes(80)),
                20,
                zoneA,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                pkg(a3, a4));
        PlannedStop pointC = pointStop(
                start.plus(Duration.ofMinutes(110)),
                20,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa0c"));
        assertThat(validator.validate(List.of(firstA, pointB, secondA, pointC), context)).isNull();
    }

    @Test
    void repeatedZoneMissingPackagesFails() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        UUID zoneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        PlannedStop first = zoneStop(
                start.plus(Duration.ofMinutes(30)),
                30,
                zoneId,
                MacroVisitKind.NEW_ZONE_VISIT,
                pkg(UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1")));
        PlannedStop second = zoneStop(
                start.plus(Duration.ofMinutes(90)),
                30,
                zoneId,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                null);
        assertThat(validator.validate(List.of(first, second), context))
                .isEqualTo("VALIDATION_FAILED: repeated zone missing packages");
    }

    @Test
    void repeatedZoneOverlappingMembersFails() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        UUID zoneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID shared = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        PlannedStop first = zoneStop(
                start.plus(Duration.ofMinutes(30)),
                30,
                zoneId,
                MacroVisitKind.NEW_ZONE_VISIT,
                pkg(shared, UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2")));
        PlannedStop second = zoneStop(
                start.plus(Duration.ofMinutes(90)),
                30,
                zoneId,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                pkg(shared, UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3")));
        assertThat(validator.validate(List.of(first, second), context))
                .isEqualTo("VALIDATION_FAILED: overlapping zone package");
    }

    @Test
    void repeatedZoneUsesZoneSubPlanMembersWhenPackageMissing() {
        PlanningContext context = validContext();
        Instant start = TripClock.startAt(context);
        UUID zoneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID a1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID a2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        PlannedStop first = zoneStop(
                start.plus(Duration.ofMinutes(30)),
                30,
                zoneId,
                MacroVisitKind.NEW_ZONE_VISIT,
                pkg(a1));
        PlannedStop second = zoneStopWithSubPlan(
                start.plus(Duration.ofMinutes(90)),
                30,
                zoneId,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                a2);
        assertThat(validator.validate(List.of(first, second), context)).isNull();
    }

    private static PlanningContext validContext() {
        return RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 400)), 8, 20),
                new com.aifishing.planning.PlanningProperties(),
                RoutePlannerHarness.launch());
    }

    private static PlannedStop stop(Instant arrival, int stayMinutes) {
        return pointStop(arrival, stayMinutes, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9"));
    }

    private static PlannedStop pointStop(Instant arrival, int stayMinutes, UUID featureId) {
        return new PlannedStop(
                RoutePlannerHarness.candidate(
                        featureId,
                        -78.92,
                        44.75,
                        0.6,
                        LightPreference.NEUTRAL,
                        FeatureType.HUMP),
                arrival,
                arrival.plus(Duration.ofMinutes(stayMinutes)),
                stayMinutes,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null);
    }

    private static PlannedStop zoneStop(
            Instant arrival,
            int stayMinutes,
            UUID zoneId,
            MacroVisitKind visitKind,
            ZoneFishingPackage fishingPackage
    ) {
        RankedCandidate candidate = zoneCandidate(zoneId);
        return new PlannedStop(
                candidate,
                arrival,
                arrival.plus(Duration.ofMinutes(stayMinutes)),
                stayMinutes,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null,
                null,
                null,
                stayMinutes,
                0,
                0,
                visitKind,
                0,
                0,
                fishingPackage);
    }

    private static PlannedStop zoneStopWithSubPlan(
            Instant arrival,
            int stayMinutes,
            UUID zoneId,
            MacroVisitKind visitKind,
            UUID memberId
    ) {
        RankedCandidate candidate = zoneCandidate(zoneId);
        RankedCandidate member = RoutePlannerHarness.candidate(
                memberId, -78.92, 44.75, 0.6, LightPreference.NEUTRAL, FeatureType.HUMP);
        member.spot().setFishingTargetId(memberId);
        ZoneSubPlan.MicroStop micro = new ZoneSubPlan.MicroStop(
                member.spot(),
                arrival,
                arrival.plus(Duration.ofMinutes(stayMinutes)),
                member.spot().getLocation(),
                member.spot().getLocation(),
                member.spot().getLocation(),
                stayMinutes,
                0,
                1.0,
                "member");
        ZoneSubPlan subPlan = new ZoneSubPlan(List.of(micro), stayMinutes, 0, 0, stayMinutes, 1.0);
        return new PlannedStop(
                candidate,
                arrival,
                arrival.plus(Duration.ofMinutes(stayMinutes)),
                stayMinutes,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null,
                null,
                subPlan,
                stayMinutes,
                0,
                0,
                visitKind,
                0,
                0,
                null);
    }

    private static RankedCandidate zoneCandidate(UUID zoneId) {
        RankedCandidate candidate = RoutePlannerHarness.candidate(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10"),
                -78.92,
                44.75,
                0.6,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        candidate.spot().setTargetKind(TargetKind.ZONE);
        candidate.spot().setZoneId(zoneId);
        candidate.spot().setVisitScopeId(zoneId);
        return candidate;
    }

    private static ZoneFishingPackage pkg(UUID... members) {
        return new ZoneFishingPackage(30, 30, 0, 0, 0, 1.0, List.of(members), null);
    }
}
