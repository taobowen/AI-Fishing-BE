package com.aifishing.planning.tactics;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TacticalRecommendationServiceTest {

    @Test
    void emptyStopsNeverFail() {
        TacticalRecommendationService service = new TacticalRecommendationService(
                new FailingHeuristic(),
                new FailingAi()
        );
        assertThatCode(() -> service.recommend(List.of(), context()))
                .doesNotThrowAnyException();
        assertThat(service.recommend(List.of(), context()).byVisitId()).isEmpty();
        assertThat(service.recommend(List.of(), context()).warning()).isNull();
    }

    @Test
    void aiAndHeuristicFailureYieldsWarningAndEmptyTactics() {
        TacticalRecommendationService service = new TacticalRecommendationService(
                new FailingHeuristic(),
                new FailingAi()
        );

        Instant now = Instant.parse("2026-09-08T12:00:00Z");
        PlannedStop stop = new PlannedStop(
                RoutePlannerHarness.candidate(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8"),
                        -78.92,
                        44.75,
                        0.6,
                        LightPreference.NEUTRAL,
                        FeatureType.HUMP),
                now,
                now.plusSeconds(1800),
                30,
                TravelEstimate.zero(),
                null,
                List.of(),
                Map.of(),
                0,
                null
        );

        TacticalRecommendationService.TacticalPlan plan = service.recommend(List.of(stop), context());
        assertThat(plan.byVisitId()).isEmpty();
        assertThat(plan.warning()).isEqualTo(TacticalRecommendationService.WARNING_UNAVAILABLE);
    }

    private static PlanningContext context() {
        var trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), FishingMode.SHORE);
        return new PlanningContext(
                trip,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                new com.aifishing.planning.PlanningProperties(),
                new java.util.ArrayList<>()
        );
    }

    private static final class FailingHeuristic extends IdealTacticHeuristic {
        @Override
        public List<StopTacticalProfile> recommend(List<FishableVisit> visits, PlanningContext context) {
            throw new IllegalStateException("heuristic down");
        }
    }

    private static final class FailingAi extends IdealTacticsAiClient {
        private FailingAi() {
            super(null, null, null);
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public List<StopTacticalProfile> recommend(List<FishableVisit> visits, PlanningContext context) {
            throw new IllegalStateException("ai down");
        }
    }
}
