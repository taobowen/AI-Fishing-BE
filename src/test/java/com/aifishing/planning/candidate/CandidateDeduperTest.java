package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.route.RoutePlannerHarness;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateDeduperTest {

    private final CandidateDeduper deduper = new CandidateDeduper();

    @Test
    void diagnoseCurrentKeepsSameSetAsDedupe() {
        List<CandidateSpot> spots = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            CandidateSpot spot = new CandidateSpot();
            spot.setFishingTargetId(UUID.nameUUIDFromBytes(("d-" + i).getBytes()));
            spot.setType(i % 2 == 0 ? FeatureType.HUMP : FeatureType.BASIN);
            spot.setStrategyWeight(0.9 - i * 0.01);
            spot.setFeatureConfidence(0.8);
            spot.setLocation(RoutePlannerHarness.point(
                    PlanningFixtures.HEAD_LNG + i * RoutePlannerHarness.metersToLng(200, PlanningFixtures.HEAD_LAT),
                    PlanningFixtures.HEAD_LAT));
            spots.add(spot);
        }
        List<CandidateSpot> kept = deduper.dedupe(spots, 150, 10, 32);
        CandidateDeduper.Result diagnosed = deduper.diagnose(spots, 150, 10, 32, CandidateDeduper.Mode.CURRENT);
        assertThat(diagnosed.kept()).containsExactlyElementsOf(kept);
        assertThat(kept).hasSizeLessThanOrEqualTo(32);
        assertThat(diagnosed.dropped(CandidateCompressionReason.FEATURE_TYPE_BUDGET)
                + diagnosed.dropped(CandidateCompressionReason.REGIONAL_CANDIDATE_BUDGET)).isPositive();
    }
}
