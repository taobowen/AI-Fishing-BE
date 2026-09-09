package com.aifishing.planning.ranking;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.GearType;
import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpotRankingServiceTest {

    private final SpotRankingService service = new SpotRankingService();

    @Test
    void emptyGearScoresHalfAndDoesNotMultiplySystemConfidence() {
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.HUMP);
        spot.setStrategyWeight(0.85);
        spot.setFeatureConfidence(0.8);
        spot.setRepresentativeDepthM(3.0);
        spot.setWindowSpecific(true);
        spot.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint());

        PlanningContext context = context(List.of());
        SpotScore score = service.score(spot, context, new DepthRange(2, 4));
        assertThat(score.breakdown().gearCompatibility()).isEqualTo(0.5);
        assertThat(score.finalScore()).isBetween(0.0, 1.0);
        assertThat(score.finalScore()).isNotEqualTo(score.finalScore() * 0.88);
    }

    @Test
    void lureInventoryDoesNotChangeGearCompatibility() {
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.HUMP);
        spot.setStrategyWeight(0.85);
        spot.setFeatureConfidence(0.8);
        spot.setRepresentativeDepthM(3.0);
        spot.setWindowSpecific(true);
        spot.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint());
        DepthRange depth = new DepthRange(2, 4);
        double empty = service.score(spot, context(List.of()), depth).breakdown().gearCompatibility();
        double luresOnly = service.score(spot, context(List.of(GearType.LURE, GearType.LURE)), depth)
                .breakdown().gearCompatibility();
        assertThat(luresOnly).isEqualTo(empty);
        assertThat(empty).isEqualTo(0.5);
    }

    @Test
    void unknownAccessUsesNeutralTravelScore() {
        CandidateSpot spot = new CandidateSpot();
        spot.setStrategyWeight(0.5);
        spot.setFeatureConfidence(0.5);
        spot.setRepresentativeDepthM(3.0);
        spot.setWindowSpecific(true);
        spot.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint());
        SpotScore score = service.score(spot, context(List.of(GearType.LURE)), new DepthRange(2, 4));
        assertThat(score.breakdown().travelAccess()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void intrinsicFishingQualityIgnoresLaunchProximity() {
        CandidateSpot near = new CandidateSpot();
        near.setStrategyWeight(0.8);
        near.setFeatureConfidence(0.8);
        near.setRepresentativeDepthM(3.0);
        near.setWindowSpecific(true);
        near.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint());
        CandidateSpot far = new CandidateSpot();
        far.setStrategyWeight(0.8);
        far.setFeatureConfidence(0.8);
        far.setRepresentativeDepthM(3.0);
        far.setWindowSpecific(true);
        far.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.04, 40).getInteriorPoint());
        PlanningContext unknown = context(List.of());
        org.locationtech.jts.geom.Point launch = ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint();
        PlanningContext withLaunch = unknown.withLaunch(
                new AccessResolution(AccessResolution.AccessStatus.AUTHORITATIVE, UUID.randomUUID(), "Ramp", launch),
                null);
        double intrinsicUnknown = service.intrinsicFishingQuality(far, unknown, new DepthRange(2, 4), EmpiricalEvidence.none());
        double intrinsicWithLaunch = service.intrinsicFishingQuality(far, withLaunch, new DepthRange(2, 4), EmpiricalEvidence.none());
        assertThat(intrinsicUnknown).isCloseTo(intrinsicWithLaunch, within(1e-9));
        assertThat(service.score(far, withLaunch, new DepthRange(2, 4)).breakdown().travelAccess())
                .isLessThan(service.score(near, withLaunch, new DepthRange(2, 4)).breakdown().travelAccess());
    }

    @Test
    void signedOntarioDepthMatchesPositiveStrategyWindow() {
        CandidateSpot spot = new CandidateSpot();
        spot.setRepresentativeDepthM(-3.2);
        assertThat(SpotRankingService.depthMatch(spot, new DepthRange(2, 4), 1.5)).isEqualTo(1.0);
    }

    @Test
    void intrinsicScoreIgnoresTripAverageWeatherCompatibilityWeight() {
        CandidateSpot spot = new CandidateSpot();
        spot.setType(FeatureType.HUMP);
        spot.setStrategyWeight(0.8);
        spot.setFeatureConfidence(0.8);
        spot.setRepresentativeDepthM(3.0);
        spot.setWindowSpecific(true);
        spot.setLocation(ProcessingFixtures.polygonSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40).getInteriorPoint());
        PlanningContext context = context(List.of());
        double withDefaultWeight = service.score(spot, context, new DepthRange(2, 4)).finalScore();
        context.properties().getRanking().setWeatherCompatibility(0.0);
        double withZeroWeight = service.score(spot, context, new DepthRange(2, 4)).finalScore();
        assertThat(withDefaultWeight).isEqualTo(withZeroWeight);
        assertThat(service.score(spot, context, new DepthRange(2, 4)).breakdown().weatherCompatibility())
                .isEqualTo(0.5);
    }

    private PlanningContext context(List<GearType> gear) {
        Trip trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), FishingMode.BOAT);
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        return new PlanningContext(
                trip,
                lake,
                null,
                AccessResolution.unknown(),
                new LakePlanningGeometry(ProcessingFixtures.polygonSquare(
                        PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 500), List.of()),
                List.of(),
                "AVAILABLE",
                PlanningFixtures.forecast(),
                gear,
                StrategyFixtures.validProfile(),
                new StrategyRun(),
                new PlanningProperties(),
                new ArrayList<>()
        );
    }
}
