package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisitOptionFactoryShareTest {

    @Test
    void firstZoneCannotConsumeEntireVisitOptionBudget() {
        PlanningProperties.Spatial spatial = new PlanningProperties.Spatial();
        spatial.setMaxPortalPairsPerZone(8);
        RankedCandidate greedy = zone("greedy", 4);
        RankedCandidate other = zone("other", 3);
        List<FishingVisitOption> unbounded = new VisitOptionFactory().options(List.of(greedy, other), spatial);
        assertThat(unbounded.stream().filter(option -> option.candidate() == greedy).count()).isGreaterThan(6);

        List<FishingVisitOption> bounded = new VisitOptionFactory().options(List.of(greedy, other), spatial, 6);
        assertThat(bounded).hasSizeLessThanOrEqualTo(6);
        long otherCount = bounded.stream().filter(option -> option.candidate() == other).count();
        assertThat(otherCount).isGreaterThanOrEqualTo(1);
        assertThat(bounded.stream().filter(option -> option.candidate() == greedy).count()).isLessThan(bounded.size());
    }

    private static RankedCandidate zone(String key, int portalCount) {
        RankedCandidate candidate = RoutePlannerHarness.candidate(
                UUID.nameUUIDFromBytes(key.getBytes()),
                PlanningFixtures.HEAD_LNG,
                PlanningFixtures.HEAD_LAT,
                0.7,
                LightPreference.NEUTRAL,
                FeatureType.DROP_OFF
        );
        candidate.spot().setTargetKind(TargetKind.ZONE);
        candidate.spot().setZoneId(UUID.nameUUIDFromBytes(key.getBytes()));
        List<VisitPortal> portals = new ArrayList<>();
        Point origin = RoutePlannerHarness.point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT);
        for (int i = 0; i < portalCount; i++) {
            portals.add(new VisitPortal("p" + i, origin));
        }
        candidate.spot().setPortals(portals);
        return candidate;
    }
}
