package com.aifishing.guidance.horizon;

import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.HorizonStep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A second stay on the same planned stop is still that stop's dwell.
 * Companions do not become another stop or another full dwell.
 */
public final class CastingOpportunityHorizon {

    private CastingOpportunityHorizon() {
    }

    public static List<HorizonStep> collapseSameStopDwell(List<HorizonStep> horizon) {
        if (horizon == null || horizon.isEmpty()) {
            return List.of();
        }
        List<HorizonStep> out = new ArrayList<>();
        for (HorizonStep step : horizon) {
            if (step == null) {
                continue;
            }
            HorizonStep previous = out.isEmpty() ? null : out.get(out.size() - 1);
            if (sameStay(previous, step)) {
                continue;
            }
            out.add(step);
        }
        return List.copyOf(out);
    }

    private static boolean sameStay(HorizonStep previous, HorizonStep step) {
        if (previous == null || step.type() != GuidanceAction.STAY || previous.type() != GuidanceAction.STAY) {
            return false;
        }
        UUID waypointId = step.tripWaypointId();
        return waypointId != null && waypointId.equals(previous.tripWaypointId());
    }
}
