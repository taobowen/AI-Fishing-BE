package com.aifishing.guidance.revisit;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.spatial.TargetKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpportunityRevisitSupportTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID POINT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID PATH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final UUID ZONE_STOP = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
    private static final UUID MEMBER_A1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID MEMBER_A2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID MEMBER_A3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID ZONE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private OpportunityRevisitSupport support;

    @BeforeEach
    void setUp() {
        support = new OpportunityRevisitSupport(
                new GuidanceProperties(),
                new SessionProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void fishedLeaveCoolsThatVisitPackageOnlyAndUsesAtomicIdentityForPointAndPath() {
        SessionWaypointProgress point = left(POINT_ID, 1, 180, NOW.minusSeconds(900));
        SessionWaypointProgress skipped = left(PATH_ID, 2, 10, NOW.minusSeconds(800));
        skipped.setStatus(WaypointProgressStatus.SKIPPED);
        SessionWaypointProgress zone = left(ZONE_STOP, 3, 300, NOW.minusSeconds(600));

        TripWaypoint pointWp = waypoint(POINT_ID, 1, TargetKind.POINT, null, List.of());
        pointWp.setFishingTargetId(POINT_ID);
        TripWaypoint pathWp = waypoint(PATH_ID, 2, TargetKind.PATH, null, List.of(PATH_ID));
        TripWaypoint zoneWp = waypoint(ZONE_STOP, 3, TargetKind.ZONE, ZONE_ID, List.of(MEMBER_A1, MEMBER_A2));

        FishingSessionState.OpportunityRevisit slice = support.assemble(
                List.of(point, skipped, zone),
                Map.of(POINT_ID, pointWp, PATH_ID, pathWp, ZONE_STOP, zoneWp),
                List.of(move(POINT_ID), stay(), move(ZONE_STOP))
        );

        assertThat(slice.packageCooldowns()).hasSize(2);
        assertThat(slice.packageCooldowns().getFirst().packageMemberIds()).containsExactly(POINT_ID);
        assertThat(slice.packageCooldowns().getFirst().cooldownUntil())
                .isEqualTo(NOW.minusSeconds(900).plusSeconds(45 * 60));
        assertThat(slice.packageCooldowns().getLast().packageMemberIds()).containsExactly(MEMBER_A1, MEMBER_A2);
        assertThat(slice.packageCooldowns().getLast().packageMemberIds()).doesNotContain(MEMBER_A3);
        assertThat(slice.packageCooldowns().getLast().physicalZoneId()).isEqualTo(ZONE_ID);
        assertThat(slice.packageCooldowns().getLast().cooldownUntil())
                .isEqualTo(NOW.minusSeconds(600).plusSeconds(60 * 60));
        assertThat(slice.lastMoveTargetTripWaypointIds()).containsExactly(POINT_ID, ZONE_STOP);
        assertThat(support.entirePackageCooling(List.of(MEMBER_A1, MEMBER_A2), slice)).isTrue();
        assertThat(support.entirePackageCooling(List.of(MEMBER_A3), slice)).isFalse();
        assertThat(support.packageEligibility(List.of(MEMBER_A1, MEMBER_A2), slice))
                .isEqualTo(OpportunityRevisitSupport.COOLED);
        assertThat(support.packageEligibility(List.of(MEMBER_A3), slice))
                .isEqualTo(OpportunityRevisitSupport.ELIGIBLE);
    }

    private static SessionWaypointProgress left(UUID tripWaypointId, int sequence, int dwellSeconds, Instant departedAt) {
        SessionWaypointProgress row = new SessionWaypointProgress();
        row.setTripWaypointId(tripWaypointId);
        row.setSequence(sequence);
        row.setStatus(WaypointProgressStatus.COMPLETED);
        row.setArrivedAt(departedAt.minusSeconds(dwellSeconds));
        row.setDepartedAt(departedAt);
        row.setAccumulatedDwellSeconds(dwellSeconds);
        return row;
    }

    private static TripWaypoint waypoint(
            UUID id,
            int sequence,
            TargetKind kind,
            UUID zoneId,
            List<UUID> members
    ) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setSequence(sequence);
        waypoint.setVisitKind(kind);
        waypoint.setTargetKind(kind);
        waypoint.setZoneId(zoneId);
        if (!members.isEmpty()) {
            waypoint.setMetadata(Map.of(
                    TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                    members.stream().map(UUID::toString).toList()
            ));
        }
        return waypoint;
    }

    private static DeliveredDecision move(UUID target) {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                POINT_ID,
                GuidanceAction.MOVE,
                null,
                target,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of("TEST"),
                "Move",
                List.of(),
                false,
                null,
                null
        );
    }

    private static DeliveredDecision stay() {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                PATH_ID,
                GuidanceAction.STAY,
                null,
                POINT_ID,
                null,
                null,
                null,
                null,
                null,
                15,
                List.of("TEST"),
                "Stay",
                List.of(),
                true,
                "VALIDATION_FAILED",
                null
        );
    }
}
