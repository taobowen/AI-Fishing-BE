package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ZoneScoreAggregationTest {

    @Test
    void scoredMembersKeepTheirAverageWhenStrategyWeightIsZero() {
        RankedCandidate first = scored(0.40, 0.20);
        RankedCandidate second = scored(0.80, 0.60);
        first.spot().setFishingTargetId(null);
        second.spot().setFishingTargetId(null);

        CandidateSpot zone = new CandidateSpot();
        zone.setFeatureId(UUID.randomUUID());
        zone.setTargetKind(TargetKind.ZONE);
        zone.setStrategyWeight(0);
        zone.setZoneMembers(List.of(first.spot(), second.spot()));

        Map<UUID, RankedCandidate> byId = new HashMap<>();
        ZoneScoreAggregation.index(byId, first);
        ZoneScoreAggregation.index(byId, second);

        RankedCandidate aggregated = ZoneScoreAggregation.aggregate(zone, byId);

        assertThat(aggregated).isNotNull();
        assertThat(aggregated.score().finalScore()).isCloseTo(0.60, within(1e-9));
        assertThat(aggregated.score().breakdown().strategyMatch()).isCloseTo(0.40, within(1e-9));
        assertThat(aggregated.score().breakdown().depthMatch()).isCloseTo(0.60, within(1e-9));
    }

    @Test
    void pathVisitUsesMemberScoresInsteadOfStrategyWeight() {
        RankedCandidate member = scored(0.53, 0.70);
        CandidateSpot path = new CandidateSpot();
        path.setFeatureId(UUID.randomUUID());
        path.setTargetKind(TargetKind.PATH);
        path.setStrategyWeight(0);
        path.setZoneMembers(List.of(member.spot()));

        Map<UUID, RankedCandidate> byId = new HashMap<>();
        ZoneScoreAggregation.index(byId, member);

        RankedCandidate aggregated = ZoneScoreAggregation.aggregate(path, byId);

        assertThat(aggregated).isNotNull();
        assertThat(aggregated.score().finalScore()).isEqualTo(0.53);
        assertThat(aggregated.score().breakdown().strategyMatch()).isEqualTo(0.70);
    }

    @Test
    void unresolvedMembersAreOmittedInsteadOfScoredZero() {
        CandidateSpot zone = new CandidateSpot();
        zone.setTargetKind(TargetKind.ZONE);
        zone.setStrategyWeight(0);
        CandidateSpot unmatched = new CandidateSpot();
        unmatched.setFeatureId(UUID.randomUUID());
        unmatched.setFishingTargetId(UUID.randomUUID());
        zone.setZoneMembers(List.of(unmatched));

        assertThat(ZoneScoreAggregation.aggregate(zone, new HashMap<>())).isNull();
    }

    private static RankedCandidate scored(double finalScore, double strategyMatch) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(UUID.randomUUID());
        spot.setFishingTargetId(UUID.randomUUID());
        spot.setTargetKind(TargetKind.POINT);
        return new RankedCandidate(spot, new SpotScore(finalScore, breakdown(strategyMatch, finalScore)), null);
    }

    private static ScoreBreakdown breakdown(double strategyMatch, double depthMatch) {
        return new ScoreBreakdown(strategyMatch, depthMatch, 0.5, 0.5, 0.5, 0.5, 0.5, null, null);
    }
}
