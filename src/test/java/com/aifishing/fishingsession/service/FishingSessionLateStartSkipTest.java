package com.aifishing.fishingsession.service;

import com.aifishing.fishingsession.dto.StartFishingSessionRequest;
import com.aifishing.fishingsession.dto.StartFishingSessionRequest.LateStartMode;
import com.aifishing.planning.domain.TripWaypoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishingSessionLateStartSkipTest {

    private static final Instant NOW = Instant.parse("2026-09-17T18:00:00Z");
    private static final UUID FIRST_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID SECOND_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final UUID ZONE_SCOPE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");

    @Test
    void expiredFirstZoneVisitDoesNotSkipLaterPackageAtSameScope() {
        TripWaypoint firstA = waypoint(FIRST_A, ZONE_SCOPE, NOW.minusSeconds(60));
        TripWaypoint secondA = waypoint(SECOND_A, ZONE_SCOPE, NOW.plusSeconds(3600));

        Set<UUID> skipIds = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, LateStartMode.SKIP_EXPIRED, null),
                List.of(firstA, secondA),
                NOW
        );

        assertThat(skipIds).containsExactly(FIRST_A);
        assertThat(FishingSessionService.matchesSkip(skipIds, firstA)).isTrue();
        assertThat(FishingSessionService.matchesSkip(skipIds, secondA)).isFalse();
    }

    @Test
    void followFullPlanAndNullLateStartSkipNone() {
        TripWaypoint firstA = waypoint(FIRST_A, ZONE_SCOPE, NOW.minusSeconds(60));
        TripWaypoint secondA = waypoint(SECOND_A, ZONE_SCOPE, NOW.plusSeconds(3600));

        Set<UUID> follow = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, LateStartMode.FOLLOW_FULL_PLAN, null),
                List.of(firstA, secondA),
                NOW
        );
        Set<UUID> omitted = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, null, null),
                List.of(firstA, secondA),
                NOW
        );

        assertThat(follow).isEmpty();
        assertThat(omitted).isEmpty();
        assertThat(FishingSessionService.matchesSkip(follow, firstA)).isFalse();
        assertThat(FishingSessionService.matchesSkip(omitted, firstA)).isFalse();
    }

    @Test
    void clientSkipVisitIdsMatchTripWaypointIdInPreferenceToSharedScope() {
        TripWaypoint firstA = waypoint(FIRST_A, ZONE_SCOPE, NOW.plusSeconds(3600));
        TripWaypoint secondA = waypoint(SECOND_A, ZONE_SCOPE, NOW.plusSeconds(7200));

        Set<UUID> skipIds = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, null, List.of(FIRST_A)),
                List.of(firstA, secondA),
                NOW
        );

        assertThat(skipIds).containsExactly(FIRST_A);
        assertThat(FishingSessionService.matchesSkip(skipIds, firstA)).isTrue();
        assertThat(FishingSessionService.matchesSkip(skipIds, secondA)).isFalse();
    }

    @Test
    void clientSkipVisitIdsStillMatchExplicitVisitId() {
        TripWaypoint firstA = waypoint(FIRST_A, ZONE_SCOPE, NOW.plusSeconds(3600));
        TripWaypoint pointC = waypoint(
                UUID.fromString("cccccccc-cccc-cccc-cccc-000000000001"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-000000000001"),
                NOW.plusSeconds(7200)
        );

        Set<UUID> skipIds = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, null, List.of(ZONE_SCOPE)),
                List.of(firstA, pointC),
                NOW
        );

        assertThat(FishingSessionService.matchesSkip(skipIds, firstA)).isTrue();
        assertThat(FishingSessionService.matchesSkip(skipIds, pointC)).isFalse();
    }

    @Test
    void lateStartOnAbacDoesNotSkipLaterAOrPointC() {
        UUID pointB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-00000000000b");
        UUID pointC = UUID.fromString("cccccccc-cccc-cccc-cccc-00000000000c");
        TripWaypoint firstA = waypoint(FIRST_A, ZONE_SCOPE, NOW.minusSeconds(60));
        TripWaypoint b = waypoint(pointB, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-00000000000d"), NOW.plusSeconds(1800));
        TripWaypoint secondA = waypoint(SECOND_A, ZONE_SCOPE, NOW.plusSeconds(3600));
        TripWaypoint c = waypoint(pointC, UUID.fromString("cccccccc-cccc-cccc-cccc-00000000000e"), NOW.plusSeconds(5400));

        Set<UUID> skipIds = FishingSessionService.resolveLateStartSkips(
                new StartFishingSessionRequest(null, LateStartMode.SKIP_EXPIRED, null),
                List.of(firstA, b, secondA, c),
                NOW
        );

        assertThat(skipIds).containsExactly(FIRST_A);
        assertThat(FishingSessionService.matchesSkip(skipIds, firstA)).isTrue();
        assertThat(FishingSessionService.matchesSkip(skipIds, b)).isFalse();
        assertThat(FishingSessionService.matchesSkip(skipIds, secondA)).isFalse();
        assertThat(FishingSessionService.matchesSkip(skipIds, c)).isFalse();
    }

    private static TripWaypoint waypoint(UUID tripWaypointId, UUID visitScopeId, Instant departure) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(tripWaypointId);
        waypoint.setVisitScopeId(visitScopeId);
        waypoint.setPlannedDepartureAt(departure);
        waypoint.setMetadata(Map.of(
                "visitId", visitScopeId.toString(),
                "visitScopeId", visitScopeId.toString()
        ));
        return waypoint;
    }
}
