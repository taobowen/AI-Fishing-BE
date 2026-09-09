package com.aifishing.planning.environment;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WhyThisTimeExplainerTest {

    @Test
    void comparativeCopyRequiresConfiguredDelta() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setWhyThisTimeMinDelta(0.08);
        properties.getEnvironment().getSolar().setMaxWeight(0.25);
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 10, 40),
                RoutePlannerHarness.hour(10, 0, 8, 10, 40),
                RoutePlannerHarness.hour(13, 30, 8, 10, 800),
                RoutePlannerHarness.hour(16, 0, 8, 10, 800)
        ), 8, 10);
        PlanningContext context = RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
        RankedCandidate candidate = RoutePlannerHarness.candidate(
                UUID.randomUUID(),
                -78.92,
                44.75,
                0.6,
                LightPreference.SUN_EXPOSED,
                FeatureType.POINT);
        TimeIndexedWeather indexed = TimeIndexedWeather.from(weather, ZoneId.of("America/Toronto"));
        LocalOrientation orientation = new LocalOrientation(270.0, null, null, false, OrientationConfidence.HIGH, "TEST");
        TimeAdjustedSpotUtility utility = new TimeAdjustedSpotUtility(new SolarPositionService(), new BoatWeatherPenalty());
        var later = ZonedDateTime.of(2026, 9, 12, 13, 30, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        WhyThisTimeExplainer.Result comparative = WhyThisTimeExplainer.explain(
                candidate, later, 30, context, indexed, orientation, utility);
        assertThat(comparative.comparative()).isTrue();
        assertThat(comparative.lines().get(0)).contains("Better after");

        properties.getSchedule().setWhyThisTimeMinDelta(5.0);
        WhyThisTimeExplainer.Result gated = WhyThisTimeExplainer.explain(
                candidate, later, 30, context, indexed, orientation, utility);
        assertThat(gated.comparative()).isFalse();
        assertThat(gated.lines().get(0)).doesNotContain("Better after");
        assertThat(gated.lines().get(0)).contains("Favorable");
    }
}
