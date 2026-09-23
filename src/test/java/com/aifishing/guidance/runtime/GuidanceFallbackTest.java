package com.aifishing.guidance.runtime;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SPOT_2;
import static org.assertj.core.api.Assertions.assertThat;

class GuidanceFallbackTest {

    @Test
    void stayDuringAdHocDoesNotForceOriginalWaypoint() {
        FishingSessionState base = GuidancePhase2Fixtures.safeState();
        FishingSessionState.Fishing fishing = new FishingSessionState.Fishing(
                SPOT_2,
                base.fishing().currentSessionWaypointProgressId(),
                base.fishing().structureType(),
                base.fishing().depthMinM(),
                base.fishing().depthMaxM(),
                base.fishing().lureFamily(),
                base.fishing().presentation(),
                base.fishing().retrieveStyle(),
                base.fishing().timeAtWaypointMinutes(),
                FishingActivityState.FISHING,
                GuidancePhase2Fixtures.AT,
                ActivityStateSource.USER_AD_HOC,
                10,
                10,
                UUID.fromString("9ba7b810-9dad-11d1-80b4-00c04fd430c8"),
                GuidancePhase2Fixtures.AT,
                null,
                null,
                null
        );
        FishingSessionState adHoc = new FishingSessionState(
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

        var delivered = GuidanceFallback.stay(
                GuidancePhase2Fixtures.DECISION_ID,
                adHoc,
                GuidanceFallback.RUN_TIMEOUT,
                "Timed out during ad-hoc"
        );

        assertThat(delivered.primaryAction()).isEqualTo(GuidanceAction.STAY);
        assertThat(delivered.targetTripWaypointId()).isNull();
        assertThat(delivered.proposedHorizon()).allMatch(step -> step.tripWaypointId() == null);
        assertThat(delivered.targetTripWaypointId()).isNotEqualTo(SPOT_2);
    }
}
