package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.RetrievedMemory;

import java.util.UUID;

/**
 * Read-only retrieval into context. Learning jobs must not run inside {@code FishingAgentFacade.run}.
 */
public interface MemoryRetrievalService {

    RetrievedMemory retrieve(UUID sessionId, FishingSessionState state, GuidanceTrigger trigger);
}
