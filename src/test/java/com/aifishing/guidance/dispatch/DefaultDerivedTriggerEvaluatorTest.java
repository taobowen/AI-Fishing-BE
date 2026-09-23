package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.WeatherCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aifishing.guidance.GuidancePhase2Fixtures.AT;
import static com.aifishing.guidance.GuidancePhase2Fixtures.TRIP_WAYPOINT;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultDerivedTriggerEvaluatorTest {

    private DefaultDerivedTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.getTriggers().setNoBiteMinutes(20);
        evaluator = new DefaultDerivedTriggerEvaluator(properties);
    }

    @Test
    void fishingAtOrAboveThresholdReturnsNoBite() {
        assertThat(evaluator.evaluate(state(FishingActivityState.FISHING, 20)))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.primary()).isEqualTo(GuidanceTrigger.NO_BITE_THRESHOLD);
                    assertThat(decision.reasonCodes()).contains("NO_BITE_THRESHOLD_CROSSED");
                });
    }

    @Test
    void belowThresholdAndNonFishingNeverReturnNoBite() {
        assertThat(evaluator.evaluate(state(FishingActivityState.FISHING, 19))).isEmpty();
        assertThat(evaluator.evaluate(state(FishingActivityState.UNKNOWN, 40))).isEmpty();
        assertThat(evaluator.evaluate(state(FishingActivityState.TRANSIT, 40))).isEmpty();
        assertThat(evaluator.evaluate(state(FishingActivityState.PAUSED, 40))).isEmpty();
    }

    private static FishingSessionState state(FishingActivityState activity, int noBiteMinutes) {
        FishingSessionState base = GuidancePhase2Fixtures.state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of()
        );
        FishingSessionState.Fishing fishing = new FishingSessionState.Fishing(
                base.fishing().currentTripWaypointId(),
                base.fishing().currentSessionWaypointProgressId(),
                base.fishing().structureType(),
                base.fishing().depthMinM(),
                base.fishing().depthMaxM(),
                base.fishing().lureFamily(),
                base.fishing().presentation(),
                base.fishing().retrieveStyle(),
                base.fishing().timeAtWaypointMinutes(),
                activity,
                AT,
                ActivityStateSource.PROGRESS,
                noBiteMinutes,
                noBiteMinutes
        );
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                fishing,
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );
    }
}
