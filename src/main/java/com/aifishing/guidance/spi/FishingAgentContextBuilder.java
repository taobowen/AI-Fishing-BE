package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.RetrievedMemory;

public interface FishingAgentContextBuilder {

    FishingAgentContext build(FishingSessionState state, GuidanceTrigger trigger, RetrievedMemory retrievedMemory);
}
