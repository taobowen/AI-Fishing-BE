package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOpportunityStateTest {

    @Test
    void atomicIdentityIsConsumedOnce() {
        RouteOpportunityState state = RouteOpportunityState.empty();
        UUID id = UUID.randomUUID();
        assertThat(state.atomicConsumed(id)).isFalse();
        state = state.consumeAtomic(id);
        assertThat(state.atomicConsumed(id)).isTrue();
    }

    @Test
    void zoneExtendDoesNotConsumeAnotherEntry() {
        UUID zoneId = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        ZoneFishingPackage first = new ZoneFishingPackage(90, 80, 10, 0, 400, 1.2, List.of(memberA), null);
        ZoneFishingPackage longer = new ZoneFishingPackage(135, 120, 15, 0, 700, 1.6, List.of(memberA, memberB), null);
        Instant t = Instant.parse("2026-09-12T12:00:00Z");
        RouteOpportunityState state = RouteOpportunityState.empty().applyZoneEntry(zoneId, first, t);
        assertThat(state.entries(zoneId)).isEqualTo(1);
        state = state.replaceZoneEntry(zoneId, first, longer, t);
        assertThat(state.entries(zoneId)).isEqualTo(1);
        assertThat(state.zone(zoneId).membersFished()).containsExactlyInAnyOrder(memberA, memberB);
    }

    @Test
    void revisitAddsASecondEntryWithoutDoubleCountingMembers() {
        UUID zoneId = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        ZoneFishingPackage first = new ZoneFishingPackage(90, 80, 10, 0, 400, 1.2, List.of(memberA), null);
        ZoneFishingPackage leftover = new ZoneFishingPackage(45, 40, 5, 0, 200, 0.4, List.of(memberB), null);
        Instant t = Instant.parse("2026-09-12T12:00:00Z");
        RouteOpportunityState state = RouteOpportunityState.empty()
                .applyZoneEntry(zoneId, first, t)
                .applyZoneEntry(zoneId, leftover, t.plusSeconds(3600));
        assertThat(state.entries(zoneId)).isEqualTo(2);
        assertThat(state.zone(zoneId).membersFished()).isEqualTo(Set.of(memberA, memberB));
        assertThat(state.canEnterZone(zoneId, 2, false)).isFalse();
        assertThat(state.canEnterZone(zoneId, 2, true)).isTrue();
    }

    @Test
    void visitKindIsExtendWhenStillAtTheZone() {
        RankedCandidate zone = RoutePlannerHarness.candidate(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10"),
                -78.92, 44.75, 0.8, LightPreference.NEUTRAL, FeatureType.FLAT);
        zone.spot().setTargetKind(TargetKind.ZONE);
        zone.spot().setZoneId(zone.spot().getFeatureId());
        FishingVisitOption option = new FishingVisitOption(
                zone, new VisitPortal("p", zone.spot().getLocation()), new VisitPortal("p", zone.spot().getLocation()), "z");
        PlannedStop stop = new PlannedStop(
                zone,
                Instant.parse("2026-09-12T12:00:00Z"),
                Instant.parse("2026-09-12T13:30:00Z"),
                90,
                TravelEstimate.zero(),
                null,
                List.of(),
                java.util.Map.of(),
                0,
                null,
                option,
                null,
                90,
                0,
                0,
                MacroVisitKind.NEW_ZONE_VISIT,
                1.0,
                0.2,
                new ZoneFishingPackage(90, 90, 0, 0, 200, 1.0, List.of(), null)
        );
        RoutePlanner.BeamState after = RoutePlanner.BeamState.initial(Instant.parse("2026-09-12T12:00:00Z"), zone.spot().getLocation())
                .child(stop, 1.0, MacroVisitKind.NEW_ZONE_VISIT, zone.spot().getFeatureId(), stop.fishingPackage(),
                        false, stop.departureAt(), stop.exitPoint(), "hour-8");
        PlanningProperties.Schedule schedule = new PlanningProperties().getSchedule();
        assertThat(RoutePlanner.visitKind(after, option, schedule)).isEqualTo(MacroVisitKind.EXTEND_CURRENT_ZONE);
    }
}
