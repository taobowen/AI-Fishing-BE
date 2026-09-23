package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OriginalPlanStep;

import java.util.UUID;

/**
 * Activity-derived session continuation when the Agent is kill-switched.
 * Not Agent authorship; callers must not persist this as delivered advice.
 */
public final class KillSwitchContinuation {

    private KillSwitchContinuation() {
    }

    public static DeliveredDecision from(UUID decisionId, FishingSessionState state) {
        GuidanceAction action = actionFor(state);
        return GuidanceFallback.fallback(
                decisionId,
                action,
                state,
                GuidanceFallback.KILL_SWITCH,
                "Agent disabled; continuing the original plan.",
                GuidanceFallback.KILL_SWITCH
        );
    }

    static GuidanceAction actionFor(FishingSessionState state) {
        if (state == null) {
            return GuidanceAction.RETURN;
        }
        FishingSessionState.Fishing fishing = state.fishing();
        FishingActivityState activity = fishing == null || fishing.activityState() == null
                ? FishingActivityState.UNKNOWN
                : fishing.activityState();
        String waypointStatus = currentWaypointProgress(state);
        UUID currentWaypoint = fishing == null ? null : fishing.currentTripWaypointId();

        if (activity == FishingActivityState.FISHING
                || "FISHING".equals(waypointStatus)
                || "ARRIVED".equals(waypointStatus)) {
            return GuidanceAction.STAY;
        }
        if ("NAVIGATING".equals(waypointStatus)
                || (activity == FishingActivityState.TRANSIT && currentWaypoint != null)) {
            return GuidanceAction.MOVE;
        }
        if (!hasRemainingPlannedTarget(state, currentWaypoint)) {
            return GuidanceAction.RETURN;
        }
        return GuidanceAction.RETURN;
    }

    private static String currentWaypointProgress(FishingSessionState state) {
        if (state.plan() == null || state.plan().originalPlanSteps() == null) {
            return null;
        }
        UUID current = state.fishing() == null ? null : state.fishing().currentTripWaypointId();
        if (current == null) {
            return null;
        }
        for (OriginalPlanStep step : state.plan().originalPlanSteps()) {
            if (step != null && current.equals(step.tripWaypointId())) {
                return step.progressStatus();
            }
        }
        return null;
    }

    private static boolean hasRemainingPlannedTarget(FishingSessionState state, UUID currentWaypoint) {
        if (currentWaypoint != null) {
            return true;
        }
        if (state.plan() == null || state.plan().originalPlanSteps() == null) {
            return false;
        }
        for (OriginalPlanStep step : state.plan().originalPlanSteps()) {
            if (step == null || step.progressStatus() == null) {
                continue;
            }
            String status = step.progressStatus();
            if ("NAVIGATING".equals(status)
                    || "ARRIVED".equals(status)
                    || "FISHING".equals(status)
                    || "UPCOMING".equals(status)) {
                return true;
            }
        }
        return false;
    }
}
