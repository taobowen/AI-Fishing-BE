package com.aifishing.planning.service;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.route.MacroVisitKind;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.strategy.domain.DepthRange;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripWaypointPlanMetadataTest {

    @Test
    void persistsMacroVisitKindAndPackageMemberIds() {
        UUID memberA = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID memberB = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10"));
        spot.setType(FeatureType.HUMP);
        spot.setLocation(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(-78.92, 44.75)));
        spot.setTargetKind(TargetKind.ZONE);
        RankedCandidate zone = new RankedCandidate(
                spot,
                new SpotScore(0.6, new ScoreBreakdown(0.6, 1, 0.8, 1, 0.5, 0.5, 0.5, 0.5, 0.0)),
                new DepthRange(2, 4));
        Instant arrival = Instant.parse("2026-09-12T12:00:00Z");
        PlannedStop stop = new PlannedStop(
                zone,
                arrival,
                arrival.plusSeconds(1800),
                30,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null,
                null,
                null,
                30,
                0,
                0,
                MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE,
                0,
                0,
                new ZoneFishingPackage(30, 30, 0, 0, 0, 1.0, List.of(memberA, memberB), null));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("visitKind", TargetKind.ZONE.name());
        TripWaypointPlanMetadata.put(metadata, stop);
        assertThat(metadata.get("visitKind")).isEqualTo(TargetKind.ZONE.name());
        assertThat(metadata.get(TripWaypointPlanMetadata.MACRO_VISIT_KIND))
                .isEqualTo(MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE.name());
        assertThat(metadata.get(TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS))
                .isEqualTo(List.of(memberA.toString(), memberB.toString()));
    }

    @Test
    void twoZoneAStopsPersistDisjointPackageMembers() {
        UUID zoneId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID a1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
        UUID a2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
        UUID a3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
        UUID a4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
        Map<String, Object> first = metadataFor(zoneId, MacroVisitKind.NEW_ZONE_VISIT, a1, a2);
        Map<String, Object> second = metadataFor(zoneId, MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE, a3, a4);

        @SuppressWarnings("unchecked")
        List<String> firstMembers = (List<String>) first.get(TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS);
        @SuppressWarnings("unchecked")
        List<String> secondMembers = (List<String>) second.get(TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS);
        assertThat(firstMembers).containsExactly(a1.toString(), a2.toString());
        assertThat(secondMembers).containsExactly(a3.toString(), a4.toString());
        assertThat(firstMembers).doesNotContainAnyElementsOf(secondMembers);
        assertThat(com.aifishing.planning.domain.TripWaypointPlanMetadata.packageMemberIds(first))
                .containsExactly(a1, a2)
                .doesNotContain(a3, a4);
        assertThat(com.aifishing.planning.domain.TripWaypointPlanMetadata.packageMemberIds(second))
                .containsExactly(a3, a4)
                .doesNotContain(a1, a2);
    }

    private static Map<String, Object> metadataFor(UUID zoneId, MacroVisitKind kind, UUID... members) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(zoneId);
        spot.setType(FeatureType.HUMP);
        spot.setLocation(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(-78.92, 44.75)));
        spot.setTargetKind(TargetKind.ZONE);
        spot.setZoneId(zoneId);
        RankedCandidate zone = new RankedCandidate(
                spot,
                new SpotScore(0.6, new ScoreBreakdown(0.6, 1, 0.8, 1, 0.5, 0.5, 0.5, 0.5, 0.0)),
                new DepthRange(2, 4));
        Instant arrival = Instant.parse("2026-09-12T12:00:00Z");
        PlannedStop stop = new PlannedStop(
                zone,
                arrival,
                arrival.plusSeconds(1800),
                30,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null,
                null,
                null,
                30,
                0,
                0,
                kind,
                0,
                0,
                new ZoneFishingPackage(30, 30, 0, 0, 0, 1.0, List.of(members), null));
        Map<String, Object> metadata = new LinkedHashMap<>();
        TripWaypointPlanMetadata.put(metadata, stop);
        return metadata;
    }
}
