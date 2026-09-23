package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.runtime.AgentRunSnapshot;

/**
 * Internal snapshot/replay API. Production callers use {@link FishingAgentFacade}.
 */
public interface FishingAgentRuntime {

    AgentRunResult execute(AgentRunSnapshot snapshot);
}
