package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;

import java.util.List;
import java.util.UUID;

/**
 * Explicit {@code runtime-mode=DETERMINISTIC} adapter. Must not be used as a
 * silent fallback when OPENAI configuration is missing.
 */
public final class DeterministicModelTurnClient implements ModelTurnClient {

    public static final String PROVIDER = "deterministic";
    public static final String MODEL_NAME = "deterministic";
    public static final String MODEL_VERSION = "v1";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public ModelTurnResult nextTurn(ModelTurnInput input) {
        FishingSessionState state = input.state();
        FishingSessionState.Fishing fishing = state == null ? null : state.fishing();
        UUID waypoint = fishing == null ? null : fishing.currentTripWaypointId();
        CandidateDecision decision = new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                input.decisionId(),
                GuidanceAction.STAY,
                null,
                waypoint,
                fishing == null ? null : fishing.lureFamily(),
                fishing == null ? null : fishing.presentation(),
                fishing == null ? null : fishing.depthMinM(),
                fishing == null ? null : fishing.depthMaxM(),
                fishing == null ? null : fishing.retrieveStyle(),
                GuidanceFallback.REEVALUATE_AFTER_MINUTES,
                List.of("DETERMINISTIC_STAY"),
                "Deterministic adapter stays on the current spot.",
                List.of(new HorizonStep(1, GuidanceAction.STAY, waypoint, GuidanceFallback.REEVALUATE_AFTER_MINUTES, true)),
                1.0
        );
        return new ModelTurnResult.FinalCandidateDecision(decision);
    }
}
