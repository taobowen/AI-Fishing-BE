package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.spi.DerivedTriggerEvaluator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Time-derived triggers. Heartbeat callers must still enforce NO_BITE crossing
 * and reset/dedup. UNKNOWN / TRANSIT / PAUSED never produce NO_BITE.
 */
@Component
public class DefaultDerivedTriggerEvaluator implements DerivedTriggerEvaluator {

    private final GuidanceProperties properties;

    public DefaultDerivedTriggerEvaluator(GuidanceProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<TriggerRoutingDecision> evaluate(FishingSessionState state) {
        if (state == null || state.fishing() == null) {
            return Optional.empty();
        }
        FishingSessionState.Fishing fishing = state.fishing();
        FishingActivityState activity = fishing.activityState() == null
                ? FishingActivityState.UNKNOWN
                : fishing.activityState();
        if (activity != FishingActivityState.FISHING) {
            return Optional.empty();
        }
        Integer noBiteMinutes = fishing.noBiteMinutes();
        if (noBiteMinutes == null || noBiteMinutes < properties.getTriggers().getNoBiteMinutes()) {
            return Optional.empty();
        }
        return Optional.of(new TriggerRoutingDecision(
                GuidanceTrigger.NO_BITE_THRESHOLD,
                List.of(),
                List.of("NO_BITE_THRESHOLD_CROSSED")
        ));
    }
}
