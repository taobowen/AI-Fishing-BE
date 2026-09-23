package com.aifishing.guidance.runtime;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.WeatherCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_1;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_3;
import static org.assertj.core.api.Assertions.assertThat;

class KillSwitchContinuationTest {

    @Test
    void fishingActivityOrArrivedWaypointStays() {
        assertThat(KillSwitchContinuation.actionFor(GuidancePhase2Fixtures.safeState()))
                .isEqualTo(GuidanceAction.STAY);
        assertThat(KillSwitchContinuation.actionFor(state(
                FishingActivityState.TRANSIT,
                SPOT_2,
                "ARRIVED"
        ))).isEqualTo(GuidanceAction.STAY);
        assertThat(KillSwitchContinuation.actionFor(state(
                FishingActivityState.UNKNOWN,
                SPOT_2,
                "FISHING"
        ))).isEqualTo(GuidanceAction.STAY);
    }

    @Test
    void navigatingOrTransitWithWaypointMoves() {
        assertThat(KillSwitchContinuation.actionFor(state(
                FishingActivityState.TRANSIT,
                SPOT_2,
                "NAVIGATING"
        ))).isEqualTo(GuidanceAction.MOVE);
        assertThat(KillSwitchContinuation.actionFor(state(
                FishingActivityState.TRANSIT,
                SPOT_2,
                "UPCOMING"
        ))).isEqualTo(GuidanceAction.MOVE);
    }

    @Test
    void noRemainingTargetReturns() {
        assertThat(KillSwitchContinuation.actionFor(state(
                FishingActivityState.TRANSIT,
                null,
                "COMPLETED"
        ))).isEqualTo(GuidanceAction.RETURN);
        assertThat(KillSwitchContinuation.actionFor(null)).isEqualTo(GuidanceAction.RETURN);
    }

    @Test
    void continuationUsesKillSwitchReasonAndIsNotAuthoredStayWithoutState() {
        var delivered = KillSwitchContinuation.from(GuidancePhase2Fixtures.DECISION_ID, GuidancePhase2Fixtures.safeState());
        assertThat(delivered.fallbackUsed()).isTrue();
        assertThat(delivered.fallbackReason()).isEqualTo(GuidanceFallback.KILL_SWITCH);
        assertThat(delivered.primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(GuidanceFallback.isKillSwitch(delivered)).isTrue();
    }

    private static FishingSessionState state(
            FishingActivityState activity,
            java.util.UUID currentWaypoint,
            String currentProgress
    ) {
        List<OriginalPlanStep> original = List.of(
                new OriginalPlanStep(1, SPOT_1, null, null, null, List.of(), "COMPLETED"),
                new OriginalPlanStep(2, SPOT_2, null, null, null, List.of(), currentProgress),
                new OriginalPlanStep(3, SPOT_3, null, null, null, List.of(), "UPCOMING")
        );
        FishingSessionState base = GuidancePhase2Fixtures.state(
                WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90,
                currentWaypoint,
                List.of(new HorizonStep(1, GuidanceAction.STAY, currentWaypoint, 15, true)),
                original
        );
        FishingSessionState.Fishing fishing = base.fishing();
        return new FishingSessionState(
                base.schemaVersion(),
                base.session(),
                base.position(),
                base.boat(),
                new FishingSessionState.Fishing(
                        currentWaypoint,
                        fishing.currentSessionWaypointProgressId(),
                        fishing.structureType(),
                        fishing.depthMinM(),
                        fishing.depthMaxM(),
                        fishing.lureFamily(),
                        fishing.presentation(),
                        fishing.retrieveStyle(),
                        fishing.timeAtWaypointMinutes(),
                        activity,
                        fishing.activityStateSince(),
                        fishing.activityStateSource(),
                        fishing.activeFishingEffortMinutes(),
                        fishing.noBiteMinutes()
                ),
                base.environment(),
                base.recent(),
                base.performance(),
                base.plan()
        );
    }
}
