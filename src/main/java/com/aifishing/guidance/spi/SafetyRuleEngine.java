package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.SafetyVerdict;

public interface SafetyRuleEngine {

    SafetyVerdict evaluate(FishingSessionState state);
}
