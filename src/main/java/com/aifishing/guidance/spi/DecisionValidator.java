package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.SafetyVerdict;

/**
 * Action applicability checks. Must trust {@code safetyVerdict} codes/allowedActions
 * and must not recompute wind or weather.
 */
public interface DecisionValidator {

    DecisionValidationResult validate(
            CandidateDecision candidate,
            FishingSessionState state,
            SafetyVerdict safetyVerdict
    );
}
