package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;

import java.util.List;
import java.util.UUID;

/**
 * Defined fallback delivery. BLOCK uses {@code prescribedAction} only —
 * this class never chooses STAY vs RETURN for a safety block.
 */
public final class GuidanceFallback {

    public static final String SAFETY_BLOCK = "SAFETY_BLOCK";
    public static final String RUN_TIMEOUT = "RUN_TIMEOUT";
    public static final String RUN_FAILED = "RUN_FAILED";
    public static final String MODEL_REFUSAL = "MODEL_REFUSAL";
    public static final String MODEL_INCOMPLETE = "MODEL_INCOMPLETE";
    public static final String MODEL_ERROR = "MODEL_ERROR";
    public static final String MODEL_ERROR_EXPLANATION = "Couldn't update advice right now. Stay on this spot.";
    public static final String MAX_MODEL_TURNS = "MAX_MODEL_TURNS";
    public static final String TOOL_BUDGET = "TOOL_BUDGET";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String KILL_SWITCH = "KILL_SWITCH";

    public static final int REEVALUATE_AFTER_MINUTES = 15;

    private GuidanceFallback() {
    }

    public static DeliveredDecision fromCandidate(CandidateDecision candidate) {
        return new DeliveredDecision(
                candidate.schemaVersion(),
                candidate.decisionId(),
                candidate.primaryAction(),
                candidate.secondaryAction(),
                candidate.targetTripWaypointId(),
                candidate.suggestedLure(),
                candidate.suggestedPresentation(),
                candidate.depthMinM(),
                candidate.depthMaxM(),
                candidate.retrieveStyle(),
                candidate.reevaluateAfterMinutes(),
                candidate.reasonCodes(),
                candidate.shortExplanation(),
                candidate.proposedHorizon(),
                false,
                null,
                null
        );
    }

    public static DeliveredDecision prescribed(
            UUID decisionId,
            GuidanceAction prescribedAction,
            FishingSessionState state,
            String explanation
    ) {
        return fallback(
                decisionId,
                prescribedAction,
                state,
                SAFETY_BLOCK,
                explanation == null || explanation.isBlank()
                        ? "Safety blocked the run; delivering the prescribed action."
                        : explanation,
                SAFETY_BLOCK
        );
    }

    public static boolean isKillSwitch(AgentRunResult result) {
        return result != null && isKillSwitch(result.delivered());
    }

    public static boolean isKillSwitch(DeliveredDecision delivered) {
        return delivered != null && KILL_SWITCH.equals(delivered.fallbackReason());
    }

    public static DeliveredDecision stay(
            UUID decisionId,
            FishingSessionState state,
            String reasonCode,
            String explanation
    ) {
        return fallback(decisionId, GuidanceAction.STAY, state, reasonCode, explanation, reasonCode);
    }

    public static DeliveredDecision fallback(
            UUID decisionId,
            GuidanceAction action,
            FishingSessionState state,
            String reasonCode,
            String explanation,
            String fallbackReason
    ) {
        FishingSessionState.Fishing fishing = state == null ? null : state.fishing();
        UUID waypoint = waypointFor(action, fishing);
        String text = clamp(explanation == null || explanation.isBlank()
                ? "Fallback decision."
                : explanation);
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                decisionId,
                action,
                null,
                waypoint,
                fishing == null ? null : fishing.lureFamily(),
                fishing == null ? null : fishing.presentation(),
                fishing == null ? null : fishing.depthMinM(),
                fishing == null ? null : fishing.depthMaxM(),
                fishing == null ? null : fishing.retrieveStyle(),
                REEVALUATE_AFTER_MINUTES,
                List.of(reasonCode),
                text,
                List.of(new HorizonStep(1, action, waypoint, REEVALUATE_AFTER_MINUTES, true)),
                true,
                clamp(fallbackReason),
                null
        );
    }

    private static UUID waypointFor(GuidanceAction action, FishingSessionState.Fishing fishing) {
        if (action == GuidanceAction.RETURN || fishing == null) {
            return null;
        }
        if (action == GuidanceAction.STAY && fishing.activityStateSource() == ActivityStateSource.USER_AD_HOC) {
            return null;
        }
        return fishing.currentTripWaypointId();
    }

    private static String clamp(String value) {
        if (value == null || value.isBlank()) {
            return "fallback";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
